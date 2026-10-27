#!/usr/bin/env bash
# cleanyourtv-foryou: build the launcher and install it on the TV with what this variant needs.
#   TV_HOST=<tv ip> ./variant-install.sh
# Wraps launcher/build-and-install.sh (which makes it Home and disables the stock launcher only once Home has moved),
# then grants the two permissions this variant's features use. Needs USB debugging on; see VARIANT.md.
set -euo pipefail
cd "$(dirname "$0")"
: "${TV_HOST:?set TV_HOST to your TV's IP address, e.g. TV_HOST=<tv ip> ./variant-install.sh}"
PY=tools/venv/bin/python
[ -x "$PY" ] || { echo "run: python3 -m venv tools/venv && tools/venv/bin/pip install -r tools/requirements.txt" >&2; exit 1; }
# The remote helper maps the app buttons (app_keys), the Settings button and Down in the screensaver.
INPUT_KEY_SERVICE=1 launcher/build-and-install.sh
# For You: read Watch Next and the dock apps' rows.
"$PY" -I tools/tv.py 'pm grant com.gathin.tvhome android.permission.READ_TV_LISTINGS && echo "For You: TV listings granted"'
# Launcher settings > Screensaver starts after: write Android's screensaver delay.
"$PY" -I tools/tv.py 'appops set com.gathin.tvhome WRITE_SETTINGS allow; appops write-settings; echo "Screensaver timer: allowed"'
echo "Done. Press Home on the remote."
