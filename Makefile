.PHONY: cf

cf:
	@echo "Memulai Cloudflare Tunnel ke localhost:8080..."
	cloudflared tunnel --url http://localhost:8080
