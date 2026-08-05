# Get current version from build.gradle
get_version() {
    grep -E '^\s*version\s*=\s*["'\'']' build.gradle | sed -E 's/^\s*version\s*=\s*["'\'']([^"'\''"]+)["'\''].*/\1/'
}

# Bump version based on type (major, minor, patch)
bump_version() {
    local version=$1
