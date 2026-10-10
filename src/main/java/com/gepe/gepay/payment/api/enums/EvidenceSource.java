package com.gepe.gepay.payment.api.enums;

public enum EvidenceSource {
    API, REPORT_FILE, BANK_STATEMENT, MANUAL,
    /** Penanda batch settlement otomatis (job Quartz, mode portofolio) — bukan bukti eksternal. */
    SYSTEM
}