// ignore: avoid_web_libraries_in_flutter, deprecated_member_use
import 'dart:html' as html;

/// Browser localStorage implementation for Web (works on HTTP and HTTPS)
void saveWebLocal(String key, String value) {
  try {
    html.window.localStorage[key] = value;
  } catch (_) {}
}

String? getWebLocal(String key) {
  try {
    return html.window.localStorage[key];
  } catch (_) {
    return null;
  }
}

void removeWebLocal(String key) {
  try {
    html.window.localStorage.remove(key);
  } catch (_) {}
}

void clearWebLocal() {
  try {
    html.window.localStorage.clear();
  } catch (_) {}
}

/// Copies [text] to the browser clipboard, working on both HTTPS and plain HTTP origins.
bool copyToWebClipboard(String text) {
  try {
    final textarea = html.TextAreaElement()
      ..value = text
      ..style.position = 'fixed'
      ..style.left = '-9999px'
      ..style.top = '0'
      ..setAttribute('readonly', '');
    html.document.body?.append(textarea);
    textarea.focus();
    textarea.select();
    textarea.setSelectionRange(0, text.length);
    final copied = html.document.execCommand('copy');
    textarea.remove();
    if (copied) return true;
  } catch (_) {}
  try {
    html.window.navigator.clipboard?.writeText(text);
    return true;
  } catch (_) {
    return false;
  }
}

