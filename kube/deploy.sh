#!/usr/bin/env bash
# deploy.sh — build and deploy the resume platform to a local Minikube cluster.
#
# Usage:
#   ./kube/deploy.sh                      # full deploy
#   ./kube/deploy.sh --skip-build         # skip Maven + Docker image builds
#   ./kube/deploy.sh --skip-infra         # skip infrastructure (re-deploy services only)
#   CLAUDE_API_KEY=sk-ant-... ./kube/deploy.sh
set -euo pipefail

# ── helpers ────────────────────────────────────────────────────────────────────

log()  { echo ""; echo "==> $*"; }
ok()   { echo "    ✓ $*"; }
fail() { echo ""; echo "ERROR: $*" >&2; exit 1; }

wait_for_pod() {
  local label=$1 timeout=${2:-120s}
  echo "    waiting for pod ($label) to be ready (timeout: $timeout)..."
  kubectl wait --for=condition=ready pod -l "$label" --timeout="$timeout" \
    || fail "Pod $label did not become ready in time. Run: kubectl describe pod -l $label"
}

# ── flags ──────────────────────────────────────────────────────────────────────

SKIP_BUILD=false
SKIP_INFRA=false
for arg in "$@"; do
  case $arg in
    --skip-build) SKIP_BUILD=true ;;
    --skip-infra) SKIP_INFRA=true ;;
    *) fail "Unknown argument: $arg" ;;
  esac
done

# ── pre-flight ─────────────────────────────────────────────────────────────────

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
KUBE="$ROOT/kube"

log "Pre-flight checks"
command -v minikube >/dev/null || fail "minikube not found"
command -v kubectl  >/dev/null || fail "kubectl not found"
command -v mvn      >/dev/null || fail "mvn not found"

if [[ -z "${CLAUDE_API_KEY:-}" ]]; then
  [[ -f "$ROOT/.env" ]] && source "$ROOT/.env"
fi
[[ -z "${CLAUDE_API_KEY:-}" ]] && fail "CLAUDE_API_KEY is not set. Export it or add it to .env"
ok "CLAUDE_API_KEY is set"

# ── minikube ───────────────────────────────────────────────────────────────────

log "Minikube"
if ! minikube status --format='{{.Host}}' 2>/dev/null | grep -q Running; then
  echo "    starting minikube..."
  minikube start --memory=3500 --cpus=4
else
  ok "already running"
fi

# Point Docker CLI at minikube's daemon so images are available in-cluster
eval "$(minikube docker-env)"
ok "Docker pointed at minikube daemon"

# ── build ──────────────────────────────────────────────────────────────────────

if [[ "$SKIP_BUILD" == false ]]; then
  log "Maven build (candidate-service, resume-parser, search-service)"
  for svc in candidate-service resume-parser search-service; do
    echo "    building $svc..."
    (cd "$ROOT/$svc" && mvn clean package -DskipTests -q)
    ok "$svc JAR built"
  done

  log "Docker image builds"
  for svc in candidate-service resume-parser search-service; do
    tag="${svc}:0.0.1-SNAPSHOT"
    echo "    building $tag..."
    docker build -t "$tag" "$ROOT/$svc" --quiet
    ok "$tag"
  done
else
  log "Skipping build (--skip-build)"
fi

# ── secret ─────────────────────────────────────────────────────────────────────

log "Claude API secret"
encoded=$(echo -n "$CLAUDE_API_KEY" | base64)
kubectl create secret generic claude-secret \
  --from-literal=CLAUDE_API_KEY="$CLAUDE_API_KEY" \
  --dry-run=client -o yaml | kubectl apply -f -
ok "claude-secret applied"

# ── infrastructure ─────────────────────────────────────────────────────────────

if [[ "$SKIP_INFRA" == false ]]; then
  log "PersistentVolumeClaim"
  kubectl apply -f "$KUBE/resume-uploads-pvc.yml"
  ok "resume-uploads-pvc"

  log "Infrastructure deployments (MySQL, Consul, Kafka, Elasticsearch, Redis)"
  kubectl apply -f "$KUBE/deployment-manifest-all.yml"
  kubectl apply -f "$KUBE/kafka-deployment.yml"
  kubectl apply -f "$KUBE/elasticsearch-deployment.yml"
  kubectl apply -f "$KUBE/redis-deployment.yml"

  log "Waiting for infrastructure to be ready"
  wait_for_pod "app=mysql"         "180s"
  wait_for_pod "app=consul"        "60s"
  wait_for_pod "app=kafka"         "120s"
  wait_for_pod "app=elasticsearch" "180s"
  wait_for_pod "app=redis"         "60s"
  ok "all infrastructure ready"
else
  log "Skipping infrastructure (--skip-infra)"
fi

# ── application services ───────────────────────────────────────────────────────

log "Application services"
kubectl apply -f "$KUBE/candidate-service-deployment.yml"
kubectl apply -f "$KUBE/resume-parser-deployment.yml"
kubectl apply -f "$KUBE/search-service-deployment.yml"

log "Waiting for application services to be ready"
wait_for_pod "app=candidate"    "120s"
wait_for_pod "app=resume-parser" "120s"
wait_for_pod "app=search"       "120s"
ok "all services ready"

# ── print URLs ─────────────────────────────────────────────────────────────────

log "Service URLs"
CANDIDATE_URL=$(minikube service candidate-service --url 2>/dev/null)
SEARCH_URL=$(minikube service search-service --url 2>/dev/null)

echo ""
echo "  Candidate Service : $CANDIDATE_URL"
echo "  Search Service    : $SEARCH_URL"
echo ""
echo "  Upload a resume:"
echo "    curl -X POST $CANDIDATE_URL/candidate-service/candidates/upload \\"
echo "      -F \"name=Jane Doe\" -F \"email=jane@example.com\" -F \"file=@resume.pdf\""
echo ""
echo "  Search candidates:"
echo "    curl -X POST $SEARCH_URL/search-service/search \\"
echo "      -H 'Content-Type: application/json' \\"
echo "      -d '{\"query\": \"Java developer with Kubernetes experience\"}'"
echo ""
echo "  Watch parser logs:"
echo "    kubectl logs -l app=resume-parser --follow"
echo ""
ok "deploy complete"
