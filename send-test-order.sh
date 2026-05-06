#!/usr/bin/env bash
set -euo pipefail

TOPIC="${KAFKA_TOPIC_ORDERS:-orders-topic}"
ORDER_ID="ORD-TEST-$(date +%s)"

PAYLOAD="{\"orderId\":\"${ORDER_ID}\",\"clientId\":\"CLI-99821\",\"items\":[{\"productId\":\"PRD-001\",\"quantity\":10,\"unitPrice\":3500.00},{\"productId\":\"PRD-008\",\"quantity\":5,\"unitPrice\":8200.00}]}"

echo "Publishing order ${ORDER_ID} to topic '${TOPIC}'..."

docker exec -i b2b-kafka kafka-console-producer \
  --bootstrap-server localhost:9092 \
  --topic "${TOPIC}" \
  --property "parse.key=true" \
  --property "key.separator=:" <<< "${ORDER_ID}:${PAYLOAD}"

echo "Done. Verifying in MongoDB (waiting 5s for processing)..."
sleep 5

docker exec b2b-mongodb mongosh b2b_orders \
  --eval "printjson(db[\"enriched-orders\"].findOne({orderId:\"${ORDER_ID}\"}))" \
  --quiet
