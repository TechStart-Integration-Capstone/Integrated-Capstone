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
