#!/bin/sh
# Install LiCal for the current user: `lical` command, launcher, today's date as icon (renewed daily
# by a user timer), Inter for the icon, the alert service. Nothing outside the home folder; data stays where it is.
set -e
cd "$(dirname "$0")"
ROOT="$(pwd)"
APP_ID=io.github.veritasx1.LiCal
DATA="${XDG_DATA_HOME:-$HOME/.local/share}"
CONFIG="${XDG_CONFIG_HOME:-$HOME/.config}"
mkdir -p "$HOME/.local/bin" "$DATA/applications" "$DATA/fonts" "$CONFIG/systemd/user"
cat > "$HOME/.local/bin/lical" <<SCRIPT
#!/bin/sh
cd "$ROOT" && exec /usr/bin/python3 -m lical "\$@"
SCRIPT
chmod +x "$HOME/.local/bin/lical"
cp lical/fonts/InterVariable.ttf "$DATA/fonts/LiCal-InterVariable.ttf"
fc-cache -f "$DATA/fonts" >/dev/null 2>&1 || true
sed "s|@EXEC@|$HOME/.local/bin/lical|" "data/$APP_ID.desktop.in" > "$DATA/applications/$APP_ID.desktop"
update-desktop-database "$DATA/applications" 2>/dev/null || true
# The icon shows today's date: renewed at every start and at midnight by this timer.
cat > "$CONFIG/systemd/user/lical-icon.service" <<UNIT
[Unit]
Description=LiCal-Icon auf das heutige Datum stellen
[Service]
Type=oneshot
ExecStart=$HOME/.local/bin/lical --icon
UNIT
cat > "$CONFIG/systemd/user/lical-icon.timer" <<UNIT
[Unit]
Description=LiCal-Icon täglich erneuern
[Timer]
OnCalendar=*-*-* 00:00:30
Persistent=true
[Install]
WantedBy=timers.target
UNIT
# Alerts before events also while the window is closed (card 91adf47e): a small service in the session.
cat > "$CONFIG/systemd/user/lical-erinnerungen.service" <<UNIT
[Unit]
Description=LiCal – Hinweise vor Terminen
PartOf=graphical-session.target
After=graphical-session.target
[Service]
ExecStart=$HOME/.local/bin/lical --erinnerungen
Restart=on-failure
RestartSec=10
[Install]
WantedBy=graphical-session.target
UNIT
systemctl --user daemon-reload 2>/dev/null && systemctl --user enable --now lical-icon.timer >/dev/null 2>&1 || true
systemctl --user enable --now lical-erinnerungen.service >/dev/null 2>&1 || true
systemctl --user restart lical-erinnerungen.service >/dev/null 2>&1 || true
"$HOME/.local/bin/lical" --icon
echo "LiCal installiert – im Anwendungsmenü oder mit: lical"
