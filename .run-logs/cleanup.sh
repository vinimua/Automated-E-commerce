#!/bin/bash
# Cleanup old and large log files — run periodically or before starting services
# Keep only last 50MB of each .log file, delete files older than 7 days

LOG_DIR="$(cd "$(dirname "$0")" && pwd)"
MAX_SIZE_MB=50
MAX_AGE_DAYS=7

echo "[cleanup] Checking logs in $LOG_DIR"

# Truncate large log files (keep last 50MB)
for f in "$LOG_DIR"/*.log; do
  if [ -f "$f" ]; then
    size_mb=$(du -m "$f" 2>/dev/null | cut -f1)
    if [ "$size_mb" -gt "$MAX_SIZE_MB" ] 2>/dev/null; then
      echo "[cleanup] Truncating $(basename "$f") (${size_mb}MB → ${MAX_SIZE_MB}MB)"
      tail -c $((MAX_SIZE_MB * 1024 * 1024)) "$f" > "$f.tmp" && mv "$f.tmp" "$f"
    fi
  fi
done

# Delete log files older than 7 days
find "$LOG_DIR" -name "*.log" -mtime +$MAX_AGE_DAYS -delete 2>/dev/null

echo "[cleanup] Done"
