#!/bin/sh
set -e

# Compose the Spring datasource JDBC URL from the discrete Render Postgres
# properties (DB_HOST/DB_PORT/DB_NAME), which are injected via render.yaml
# fromDatabase. This avoids relying on Render's postgres:// connection string,
# which the JDBC driver cannot parse directly.
#
# If SPRING_DATASOURCE_URL is already set (e.g. an external/SSL connection),
# it is kept as-is.
if [ -z "${SPRING_DATASOURCE_URL:-}" ] && [ -n "${DB_HOST:-}" ]; then
  export SPRING_DATASOURCE_URL="jdbc:postgresql://${DB_HOST}:${DB_PORT:-5432}/${DB_NAME:-resolveit}"
fi

exec java $JAVA_OPTS -jar app.jar
