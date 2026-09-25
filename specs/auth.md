# Panduan Implementasi — Firebase Auth + Identity Module

> Disinkronkan dengan kode yang berjalan. Fokus: autentikasi & RBAC identitas.
> Modul donasi/content-creator belum ada; catatan integrasinya di bagian akhir.

---

## 0. Model & keputusan desain

### Role (RBAC polos, tanpa branch)

| Role          | Arti                                                             |
|---------------|------------------------------------------------------------------|
| `SUPER_ADMIN` | Pemilik platform. Satu-satunya yang boleh grant/revoke role.     |
| `ADMIN`       | Operator platform (belum dipakai untuk gate endpoint saat ini).  |
| `CREATOR`     | Content creator yang jual konten / terima donasi.                |
| `USER`        | User biasa (donor/pembeli). Diberikan otomatis saat signup.      |

Semua role bersifat **global** — tidak ada `branch_id`, tidak ada invariant
"global XOR branch-scoped". Satu user boleh punya banyak role lewat tabel
`identity.user_roles` (mis. `USER` + `CREATOR`, atau `ADMIN`).

`CREATOR` sengaja cuma role (flag permission) di sini; profil creator, payout,
overlay, dsb. adalah modul terpisah nanti.

### Registrasi & provisioning

- **Donasi boleh tanpa login** → endpoint donasi nanti `permitAll`, tidak
  menyentuh `identity` sama sekali (tidak perlu baris `users`).
- **Beli konten / aksi creator wajib login** → endpoint-nya `authenticated`.
- **User biasa self-register**: frontend bikin akun Firebase, lalu request
  pertama yang terautentikasi memicu `provisionOnFirstLogin` → baris
  `identity.users` + role `USER` dibuat otomatis (JIT provisioning).
- **Admin/creator** dibuat dengan cara: login seperti user biasa dulu, lalu
  `SUPER_ADMIN` grant role-nya (`POST /roles`). Tidak ada lagi endpoint
  *invite* dan tidak ada pembuatan akun Firebase dari backend.

### Seeder SUPER_ADMIN (bootstrap)

`V3__identity_tables.sql` men-seed satu baris user `SUPER_ADMIN` **tanpa
`auth_id`**. Saat email itu pertama kali login (Google/credentials), JIT
provisioning menemukan baris by email, meng-`linkAuthId`, dan **tidak
menghapus role** yang sudah ada. Jadi tidak ada chicken-and-egg.

### Invariant keamanan (wajib)

1. **`emailVerified` wajib** sebelum link/provision. Tanpa ini, orang bisa
   mendaftarkan email orang lain di Firebase (belum terverifikasi) lalu
   mengklaim baris pre-seeded (mis. SUPER_ADMIN). Klaim email lewat Google
   otomatis verified karena Google memverifikasi kepemilikan email.
2. **Link ditolak kalau `auth_id` sudah terisi UID lain** (`User.linkAuthId`).
   Satu email = satu akun Firebase = satu user.
3. **User `DISABLED` selalu ditolak** dengan **403** `identity.user.disabled`
   (bukan 401 generik). `resolveByAuthId` sengaja mengembalikan principal apa
   adanya (termasuk `DISABLED`) supaya `IdentityEnrichmentFilter` bisa
   membedakan "disuspend" dari "belum dikenal", dan statusnya ikut ter-cache.
4. `SUPER_ADMIN` **tidak boleh mencabut role `SUPER_ADMIN` miliknya sendiri**
   (mencegah lockout).

### Dua kontrak terpisah: `api/dtos` vs entity

- **`api/dtos`** = kontrak lintas modul (controller sendiri + modul lain via
  `IdentityApi`). `record` polos, tanpa validation annotation, tanpa referensi
  JPA/entity. Enum `Role`/`UserStatus` di `api/dtos` terpisah dari
  `internal/entity.Role`/`User.Status`; dipetakan eksplisit di service.
  `api` tidak pernah import tipe `internal` (`agents.md` §2.1).
- **`internal/delivery/http/req`** = request DTO HTTP — di sinilah validation
  annotation hidup. Controller memetakan `req` → command `api/dtos`.

### Pembagian tanggung jawab authorization

| Layer                             | Tugas                                                     |
|-----------------------------------|------------------------------------------------------------|
| Spring Security (`@PreAuthorize`) | Role apa yang boleh menyentuh endpoint — gerbang kasar     |
| Service (`CurrentUser`)           | Resource spesifik mana yang boleh diakses — gerbang halus  |

---

## 1. Skema (`V3__identity_tables.sql`)

```sql
CREATE SCHEMA IF NOT EXISTS identity;
CREATE EXTENSION IF NOT EXISTS citext;

CREATE TABLE identity.users
(
    id         UUID PRIMARY KEY,
    auth_id    VARCHAR(128) UNIQUE,   -- null = belum pernah login
    email      citext       NOT NULL,
    name       VARCHAR(100),          -- nullable: Firebase gak jamin displayName
    status     VARCHAR(20)  NOT NULL, -- ACTIVE / DISABLED
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX ux_identity_users_email ON identity.users (email);

CREATE TABLE identity.user_roles
(
    id         UUID        PRIMARY KEY,          -- UUID v7, dibuat aplikasi
    user_id    UUID        NOT NULL REFERENCES identity.users (id) ON DELETE CASCADE,
    role       VARCHAR(30) NOT NULL,             -- SUPER_ADMIN/ADMIN/CREATOR/USER
    granted_by UUID,                             -- null = self/system
    granted_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ux_identity_user_role UNIQUE (user_id, role)
);
```

Catatan:
- `name` **nullable** karena Firebase tidak menjamin `displayName`.
- `user_roles` punya `id` UUID v7 + unique `(user_id, role)` (sesuai kebijakan
  id aplikasi `agents.md` §3); grant bersifat idempotent di level DB.
- Enum disimpan sebagai `varchar` (bukan tipe enum DB) agar nambah role tidak
  perlu migrasi.

Migrasi dianggap **belum pernah diterapkan** di DB dev, jadi V3 ditulis ulang.
Kalau V3 sudah jalan di suatu DB: drop schema `identity` + hapus baris V3 dari
`flyway_schema_history`, atau buat migrasi V4 baru.

---

## 2. Alur autentikasi

```
1. User login (Google / email+password) di client → dapat Firebase ID token.
2. Frontend WAJIB langsung hit GET /api/v1/identities/me (bukan cuma simpan token).
3. Backend:
   FirebaseAuthenticationFilter  → verifikasi token → FirebasePrincipal(authId,email,name,verified)
   IdentityEnrichmentFilter      → resolveByAuthId(authId)
        ada & DISABLED → 403 identity.user.disabled (stop)
        ada & ACTIVE   → pakai
        tidak ada      → provisionOnFirstLogin(authId,email,name,verified)
                           emailVerified?  tidak → 401
                           by authId?      → user itu
                           by email?       → link + pertahankan role (baris pre-seeded)
                           tidak ada?      → buat user + role USER
                           (kalau akun DISABLED → 403 identity.user.disabled)
4. Principal dipasang ke SecurityContext, role jadi authority ROLE_<ROLE>.
5. 200 → simpan principal di state → lanjut.
   401 → email belum terverifikasi / tidak dikenal → signOut Firebase.
   403 (code `identity.user.disabled`) → akun disuspend → signOut + pesan jelas.
```

Request berikutnya me-resolve principal lewat cache Redis
(`resolveByAuthId`, TTL 10 menit). Lihat §5 untuk jaminan eviction.

---

## 3. Endpoint

| Method | Path                          | Akses         | Fungsi                                   |
|--------|-------------------------------|---------------|------------------------------------------|
| GET    | `/api/v1/identities/me`       | authenticated | Principal user saat ini                  |
| POST   | `/api/v1/identities/roles`    | SUPER_ADMIN   | Grant role (`{email, role}`)             |
| DELETE | `/api/v1/identities/roles`    | SUPER_ADMIN   | Revoke role (`{email, role}`)            |

Contoh grant creator:

```bash
curl -X POST localhost:8080/api/v1/identities/roles \
  -H "Authorization: Bearer <SUPER_ADMIN token>" \
  -H 'Content-Type: application/json' \
  -d '{"email":"creator@example.com","role":"CREATOR"}'
```

Request DTO ada di `internal/delivery/http/req/GrantRoleReq.java`; command-nya
`api/dtos/GrantRoleCommand.java`.

---

## 4. Konfigurasi & kredensial Firebase

- Kredensial service account (base64) lewat env `FIREBASE_CREDENTIALS_BASE64`
  (jangan commit). `FirebaseConfig` fail-fast kalau kosong.
- `FirebaseConfig` di-*exclude* pada profil `test`; test memakai
  `TestFirebaseAuthConfig` (mock `FirebaseAuth`) + `application-test.yaml`
  (nilai dev lokal untuk Postgres/Redis/CORS), sehingga `./mvnw test` tidak
  butuh kredensial asli.
- Verifikasi token: `verifyIdToken(token, false)` — signature/expiry dicek
  lokal, JWKS Google di-cache SDK, `checkRevoked=false` menghindari hit server
  tiap request.

---

## 5. Caching

`IdentityCacheConfig` mendeklarasikan cache `identity-principal-by-auth-id`
(nilai `UserPrincipal`, TTL 10 menit) via `CacheSpec`. `resolveByAuthId`
`@Cacheable(..., unless = "#result == null")` — `unless` penting karena
`CacheConfig` menolak nilai null, dan user yang belum ke-link tidak boleh
"nyangkut" di-cache sebagai tidak dikenal. User `DISABLED` **ikut ter-cache**
(karena principal apa adanya), jadi tidak hit DB tiap request.

**Eviction instan (best practice).** `CacheConfig` dibangun dengan
`.transactionAware()`, sehingga operasi cache yang dikeluarkan dari dalam
transaksi baru dijalankan **setelah commit**; Redis dipakai bersama, jadi semua
instance langsung melihatnya. `PrincipalCache.evict(authId)` melakukan evict
**targeted** (bukan `allEntries`) supaya tidak terjadi thundering herd.

Aturan: **setiap mutasi user** (grant/revoke role, disable/activate,
ubah email/nama nanti) WAJIB memanggil `PrincipalCache.evict(authId)`. TTL 10
menit hanya jaring pengaman — perilaku user berubah seketika, tidak menunggu
token Firebase habis. Catatan: cache-aside punya race kecil (reader lama bisa
menulis balik nilai lama sesudah evict); TTL membatasinya.

---

## 6. Struktur file

```
identity/
├── package-info.java                     @ApplicationModule(id = "identity")
├── api/
│   ├── package-info.java                 @NamedInterface("api")
│   ├── IdentityApi.java                  resolveByAuthId / provisionOnFirstLogin / grantRole / revokeRole
│   ├── CurrentUser.java                  helper principal untuk service modul lain
│   └── dtos/                             Role, UserStatus, UserPrincipal, UserResponse, GrantRoleCommand
└── internal/
    ├── cache/PrincipalCache.java         targeted evict principal by authId
    ├── config/IdentityCacheConfig.java
    ├── config/SecurityConfig.java        @EnableMethodSecurity, filter chain, CORS
    ├── delivery/http/IdentityController.java
    ├── delivery/http/req/GrantRoleReq.java
    ├── entity/User.java, UserRole.java, Role.java
    ├── repository/UserRepository.java, UserRoleRepository.java
    ├── security/IdentityEnrichmentFilter.java
    ├── exception/IdentityError.java
    └── service/IdentityServiceImpl.java
```

---

## 7. Test

- `IdentityServiceImplTest` (integrasi, profil `test`): auto-provision user
  baru, tolak login belum terverifikasi, link seeder + pertahankan
  `SUPER_ADMIN`, grant/revoke role, resolve user `DISABLED`, larangan cabut
  SUPER_ADMIN sendiri.
- `IdentityEnrichmentFilterTest` (unit, tanpa context): akun `DISABLED` → 403
  `identity.user.disabled`; akun aktif → principal terpasang & chain lanjut.
- `ApplicationTests` + `TestFirebaseAuthConfig`: context load tanpa Firebase asli.
- `ModularityTests.verifyModularity()` wajib hijau.

---

## 8. Checklist

- [x] Tanpa `branch_id` dan invariant branch-scoped.
- [x] `emailVerified` wajib untuk link/provision (anti email-claiming).
- [x] Tolak link kalau `auth_id` sudah milik UID lain.
- [x] User `DISABLED` dibalas 403 `identity.user.disabled` (bukan 401 generik).
- [x] `SUPER_ADMIN` tidak bisa cabut role-nya sendiri.
- [x] `api/dtos` tidak import `internal`.
- [x] `resolveByAuthId` pakai `unless = "#result == null"`.
- [x] Eviction targeted by `authId` (`PrincipalCache`) di setiap mutasi.
- [x] `./mvnw test` hijau.

---

## 9. Integrasi modul berikutnya

- **Donasi (anonim boleh)** — endpoint `permitAll`; kalau ada Bearer token yang
  valid, principal ikut terisi (opsional), kalau tidak ya anonim. Jangan
  panggil `currentUser.get()` tanpa null-check di jalur anonim.
- **Beli konten** — `@PreAuthorize("isAuthenticated()")` atau
  `hasRole('USER')`.
- **Aksi creator** — `@PreAuthorize("hasRole('CREATOR')")`; authz kepemilikan
  resource spesifik di service via `CurrentUser`.
- **Grant creator** — `SUPER_ADMIN` panggil `POST /roles` dengan role
  `CREATOR` (atau nanti lewat halaman admin / onboarding).
