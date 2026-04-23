# MIDAS Chrome Extension — packaging
#
# Usage:
#   make help      ← show targets
#   make zip       ← build dist/midas-extension-<version>.zip (Web Store upload)
#   make crx       ← build dist/midas-extension-<version>.crx (direct distribution)
#   make package   ← both
#   make publish   ← package + copy latest to apps/chrome-extension.{crx,zip}
#   make validate  ← sanity-check manifest.json
#   make info      ← show version + artifact sizes
#   make clean     ← remove dist/

SHELL := /bin/bash

EXT_DIR    := $(CURDIR)
DIST_DIR   := $(EXT_DIR)/dist
NAME       := midas-extension
VERSION    := $(shell python3 -c "import json; print(json.load(open('$(EXT_DIR)/manifest.json'))['version'])")
KEY        := $(EXT_DIR)/../chrome-extension.pem
PARENT_CRX := $(EXT_DIR)/../chrome-extension.crx
PARENT_ZIP := $(EXT_DIR)/../chrome-extension.zip
ZIP        := $(DIST_DIR)/$(NAME)-$(VERSION).zip
CRX        := $(DIST_DIR)/$(NAME)-$(VERSION).crx
# Files included in the package — explicit allow-list keeps secrets and junk out.
SRC_FILES  := manifest.json src

.PHONY: help zip crx package publish validate keygen info clean

help:
	@echo "MIDAS Chrome Extension v$(VERSION)"
	@echo
	@echo "Targets:"
	@echo "  zip       build $(ZIP)"
	@echo "  crx       build $(CRX) (signed with $(notdir $(KEY)))"
	@echo "  package   both zip and crx"
	@echo "  publish   package + copy latest to apps/chrome-extension.{crx,zip}"
	@echo "  validate  sanity-check manifest.json"
	@echo "  keygen    generate $(notdir $(KEY)) if missing"
	@echo "  info      show version + artifact sizes"
	@echo "  clean     remove dist/"

validate:
	@python3 -c "\
import json, sys; \
m = json.load(open('$(EXT_DIR)/manifest.json')); \
required = ['manifest_version', 'name', 'version']; \
missing = [k for k in required if k not in m]; \
sys.exit('manifest.json missing keys: ' + ', '.join(missing)) if missing else None; \
print(f\"  manifest ok: {m['name']} v{m['version']} (mv{m['manifest_version']})\")"

$(DIST_DIR):
	@mkdir -p $(DIST_DIR)

# Web Store upload zip — no .crx/.pem/dist/ inside.
zip: validate $(DIST_DIR)
	@rm -f $(ZIP)
	@cd $(EXT_DIR) && zip -rq $(ZIP) $(SRC_FILES) \
	  -x "*.DS_Store" "*/.DS_Store" "*.pem" "*.crx" "dist/*" "Makefile" "README.md"
	@echo "→ $(ZIP) ($$(du -h $(ZIP) | cut -f1))"

keygen:
	@if [ -f $(KEY) ]; then \
	  echo "  $(KEY) already exists — keeping it (extension ID is derived from this key)"; \
	else \
	  openssl genrsa -out $(KEY) 2048 2>/dev/null; \
	  chmod 600 $(KEY); \
	  echo "  → generated $(KEY) — DO NOT commit this file"; \
	fi

# Signed .crx via crx3 npm package (no Chrome browser required).
# crx3 produces both a CRXv3 file and a zip; we use the .crx and discard the zip.
crx: validate keygen $(DIST_DIR)
	@command -v npx >/dev/null 2>&1 || { \
	  echo "ERROR: npx not found. Install Node.js (or use 'make zip' + load unpacked)."; exit 1; }
	@rm -f $(CRX)
	@npx --yes crx3@1.1.3 \
	  --keyPath="$(KEY)" \
	  --crxPath="$(CRX)" \
	  "$(EXT_DIR)/manifest.json" >/dev/null
	@echo "→ $(CRX) ($$(du -h $(CRX) | cut -f1))"

package: zip crx

# Copy latest artifacts to stable paths (apps/chrome-extension.{crx,zip}) for
# anything that hardcodes those paths (CI, manual install instructions, etc.).
publish: package
	@cp $(ZIP) $(PARENT_ZIP)
	@cp $(CRX) $(PARENT_CRX)
	@echo "→ $(PARENT_ZIP)"
	@echo "→ $(PARENT_CRX)"

info:
	@echo "version: $(VERSION)"
	@echo "key:     $(KEY) ($$([ -f $(KEY) ] && echo present || echo MISSING))"
	@echo "dist:    $(DIST_DIR)"
	@if [ -d $(DIST_DIR) ]; then \
	  ls -lh $(DIST_DIR)/ 2>/dev/null | tail -n +2 | awk '{print "  " $$NF " " $$5}'; \
	fi

clean:
	@rm -rf $(DIST_DIR)
	@echo "  removed $(DIST_DIR)"
