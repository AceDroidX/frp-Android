#!/usr/bin/env bash
# update_frp_binaries.sh
# Downloads the latest frp release (or a specified tag), extracts required three arch tarballs,
# and places frps & frpc executables into the jniLibs directories with names libfrps.so and libfrpc.so
# Usage:
#   ./scripts/update_frp_binaries.sh [--tag <TAG>] [--dest <DEST_BASE>] [--dry-run] [--token <GH_TOKEN>]

set -euo pipefail

REPO_OWNER="fatedier"
REPO_NAME="frp"
API_BASE="https://api.github.com/repos/${REPO_OWNER}/${REPO_NAME}/releases"

# Default dest base directory
DEST_BASE_DEFAULT="app/src/main/jniLibs"
TAG=""
DEST_BASE="${DEST_BASE_DEFAULT}"
DRY_RUN=0
GITHUB_TOKEN=""

# Helper function
err() { echo "[ERROR] $*" >&2; }
log() { echo "[INFO] $*"; }
usage() {
  cat <<EOF
Usage: $0 [--tag <TAG>] [--dest <DEST_BASE>] [--dry-run] [--token <GH_TOKEN>]

Downloads the latest frp release (or provided tag), extracts three asset tarballs and
copies the frpc and frps binaries into Android JNI libs directories as libfrpc.so and libfrps.so
Mapping (default):
  frp_*_android_arm64.tar.gz -> ${DEST_BASE}/arm64-v8a
  frp_*_linux_amd64.tar.gz -> ${DEST_BASE}/x86_64
  frp_*_linux_arm.tar.gz -> ${DEST_BASE}/armeabi-v7a

Options:
  --tag     : Release tag (default: latest)
  --dest    : Base destination directory (default: ${DEST_BASE_DEFAULT})
  --dry-run : Show actions but don't download or write files
  --token   : (Optional) GitHub token to increase rate limit
EOF
}

# Parse args
while [[ $# -gt 0 ]]; do
  case "$1" in
    --tag) TAG="$2"; shift 2;;
    --dest) DEST_BASE="$2"; shift 2;;
    --dry-run) DRY_RUN=1; shift;;
    --token) GITHUB_TOKEN="$2"; shift 2;;
    -h|--help) usage; exit 0;;
    *) err "Unknown arg: $1"; usage; exit 1;;
  esac
done

# Check required tools
for cmd in curl jq tar mkdir mktemp grep; do
  if ! command -v "$cmd" >/dev/null 2>&1; then
    err "Required command '$cmd' not found. Please install it."; exit 2
  fi
done

# Prepare request URL
if [[ -n "$TAG" ]]; then
  API_URL="${API_BASE}/tags/${TAG}"
else
  API_URL="${API_BASE}/latest"
fi

log "Fetching release info from GitHub: ${API_URL}"

AUTH_ARGS=()
if [[ -n "$GITHUB_TOKEN" ]]; then
  AUTH_ARGS=( -H "Authorization: token ${GITHUB_TOKEN}" )
fi

if [[ $DRY_RUN -eq 1 ]]; then
  log "DRY RUN: will not perform downloads or write files. Showing intended behavior..."
fi

# Get release JSON
# Fetch release JSON (fail hard if GitHub returns an error)
if ! release_json=$(curl -sSL --fail "${API_URL}" -H "Accept: application/vnd.github.v3+json" "${AUTH_ARGS[@]}" ); then
  err "Failed to fetch release info from GitHub (${API_URL})"; exit 3
fi
if [[ -z "${release_json}" || "${release_json}" == "null" ]]; then
  err "Release info is empty or null from GitHub"; exit 3
fi

# Architecture mapping
declare -A ARCH_MAP
ARCH_MAP["arm64-v8a"]="android_arm64"
ARCH_MAP["x86_64"]="linux_amd64"
ARCH_MAP["armeabi-v7a"]="linux_arm"

# Make DEST_BASE if not exists
if [[ $DRY_RUN -eq 0 && ! -d ${DEST_BASE} ]]; then
  log "Creating dest base ${DEST_BASE}"
  mkdir -p "${DEST_BASE}"
fi

TMP_BASE=$(mktemp -d)
trap '[[ -d "$TMP_BASE" ]] && rm -rf "$TMP_BASE"' EXIT

log "Temporary work dir: ${TMP_BASE}"

# Function to find an asset for a given pattern
find_asset_url() {
  local pattern="$1"  # pattern to match like 'linux_amd64' or 'android_arm64'
  # Build a regex: frp_.*_${pattern}.*.(tar.gz|zip)
  local regex="frp_.*_${pattern}.*\\.(tar\\.gz|zip)$"

  # Use jq to find browser_download_url matching regex
  local url
  url=$(jq -r --arg re "${regex}" '.assets[] | select(.name | test($re)) | .browser_download_url' <<<"${release_json}" | head -n 1)
  if [[ -z "$url" || "$url" == "null" ]]; then
    # fallback: contains substring
    url=$(jq -r --arg s "${pattern}" '.assets[] | select(.name | contains($s)) | .browser_download_url' <<<"${release_json}" | head -n 1)
  fi
  printf '%s' "$url"
}

# Function to process an asset: download, extract frpc / frps, copy to dest with rename
process_asset() {
  local pattern="$1"   # e.g. linux_amd64
  local abi_dir="$2"   # e.g. arm64-v8a

  local asset_url
  asset_url=$(find_asset_url "$pattern")
  if [[ -z "$asset_url" || "$asset_url" == "null" ]]; then
    err "Cannot find asset for pattern '${pattern}' (abi ${abi_dir}) in release. Skipping..."
    # 1 = asset not found (non-fatal when trying alternate candidates)
    return 1
  fi

  log "Found asset for ${abi_dir}: ${asset_url}"

  local filename
  filename="${TMP_BASE}/$(basename "${asset_url}")"

  if [[ $DRY_RUN -eq 1 ]]; then
    log "DRY RUN: would download ${asset_url} to ${filename}"
  else
    log "Downloading ${asset_url}..."
    if ! curl -sSL --fail -o "${filename}" "${asset_url}"; then
      err "Download failed for ${asset_url}";
      # 2 = download failed (fatal)
      return 2
    fi
  fi

  local extract_dir="${TMP_BASE}/extract_${abi_dir}"
  mkdir -p "${extract_dir}"

  # List contents and try to find frpc & frps paths
  if [[ $DRY_RUN -eq 1 ]]; then
    # When doing a dry-run, show the asset name and URL we would download.
    local asset_name
    asset_name=$(jq -r --arg s "${pattern}" '.assets[] | select(.name | test($s)) | .name' <<<"${release_json}" | head -n 1)
    log "DRY RUN: Would download asset: ${asset_name}"
    log "DRY RUN: Asset URL: ${asset_url}"
    log "DRY RUN: Would extract frpc & frps and place into ${DEST_BASE}/${abi_dir} as libfrpc.so and libfrps.so"
    return 0
  else
    # Get file list
    if ! file_list=$(tar -tzf "${filename}"); then
      err "Failed to list archive ${filename}";
      # 3 = invalid archive or listing failed (fatal)
      return 3
    fi

    frpc_path=$(grep -E '/frpc$|(^|/)frpc$' <<<"${file_list}" | head -n1 || true)
    frps_path=$(grep -E '/frps$|(^|/)frps$' <<<"${file_list}" | head -n1 || true)

    if [[ -z "$frpc_path" || -z "$frps_path" ]]; then
      # Maybe frpc is named differently (e.g. frp or frp_client?). Search more broadly
      frpc_path=$(grep -E '(^|/)frpc$|(^|/)frp$' <<<"${file_list}" | head -n1 || true)
      frps_path=$(grep -E '(^|/)frps$|(^|/)frp' <<<"${file_list}" | head -n1 || true)
    fi

    if [[ -z "$frpc_path" || -z "$frps_path" ]]; then
      err "frpc or frps not found in ${filename}. Listing files and skipping"
      echo "$file_list" | sed -n '1,100p'
      return 1
    fi

    log "Extracting $frpc_path and $frps_path to ${extract_dir}"
    if ! tar -xzf "${filename}" -C "${extract_dir}" "$frpc_path" "$frps_path"; then
      err "Extraction failed for ${filename}";
      # 4 = extraction failed (fatal)
      return 4
    fi

    # The extracted files will be at ${extract_dir}/$frpc_path, get their basenames
    frpc_basename=$(basename "$frpc_path")
    frps_basename=$(basename "$frps_path")

    src_frpc="${extract_dir}/${frpc_path}"
    src_frps="${extract_dir}/${frps_path}"

    if [[ ! -f "$src_frpc" ]]; then
      # If extraction put file without path
      src_frpc=$(find "${extract_dir}" -type f -name "$frpc_basename" | head -n1 || true)
    fi
    if [[ ! -f "$src_frps" ]]; then
      src_frps=$(find "${extract_dir}" -type f -name "$frps_basename" | head -n1 || true)
    fi

    if [[ -z "$src_frpc" || -z "$src_frps" ]]; then
      err "Failed to locate extracted frpc/frps files. Please inspect ${extract_dir}"
      ls -l "${extract_dir}" || true
      # 5 = missing expected binaries in archive (fatal)
      return 5
    fi

    dest_dir="${DEST_BASE}/${abi_dir}"
    if [[ ! -d "${dest_dir}" ]]; then
      log "Creating dest dir ${dest_dir}"
      mkdir -p "${dest_dir}"
    fi

    # Write to the dest with required names
    out_frpc="${dest_dir}/libfrpc.so"
    out_frps="${dest_dir}/libfrps.so"

    log "Copying ${src_frpc} -> ${out_frpc}"
    cp -f "${src_frpc}" "${out_frpc}"
    chmod +x "${out_frpc}"

    log "Copying ${src_frps} -> ${out_frps}"
    cp -f "${src_frps}" "${out_frps}"
    chmod +x "${out_frps}"

    log "Updated jniLibs for ${abi_dir}:"
    ls -l "${dest_dir}"
  fi

  return 0
}

# Process each ARCH
for abi in "${!ARCH_MAP[@]}"; do
  mapping=${ARCH_MAP[$abi]}
  # For linux arm, accept both linux_arm and linux_arm_hf (hf = hardware float) in matching
  if [[ "$mapping" == "linux_arm" ]]; then
    # 优先尝试 linux_arm_hf，其次退回 linux_arm
    pattern_candidates=("linux_arm_hf" "linux_arm")
  elif [[ "$mapping" == "android_arm64" ]]; then
    # 旧版本缺少 android_arm64 资产时，回退到 linux_arm64
    pattern_candidates=("android_arm64" "linux_arm64")
  else
    pattern_candidates=("${mapping}")
  fi

  downloaded=1
  for candidate in "${pattern_candidates[@]}"; do
    if process_asset "$candidate" "$abi"; then
      downloaded=0
      break
    else
      rc=$?
      # If asset wasn't found for this candidate, try the next candidate (rc == 1)
      if [[ $rc -eq 1 ]]; then
        log "Asset not found for candidate '${candidate}', trying next candidate if available..."
        continue
      else
        err "Fatal error processing asset for abi ${abi} (candidate: ${candidate}), exit code ${rc}. Exiting immediately."
        exit $rc
      fi
    fi
  done

  if [[ $downloaded -ne 0 ]]; then
    err "No suitable asset found for abi ${abi}. Exiting with code 6."
    exit 6
  fi
done

log "All done."

if [[ $DRY_RUN -eq 1 ]]; then
  log "DRY RUN complete. No files were written."
fi

exit 0
