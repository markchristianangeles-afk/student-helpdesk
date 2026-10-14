// Request bodies are untrusted JSON: a client sending a number or object where
// a string is expected used to reach .trim() and throw. Anything that is not a
// string collapses to null so callers can reject it as missing.
function trimmedString(value) {
  return typeof value === 'string' ? value.trim() : null;
}

module.exports = { trimmedString };
