// Expo config plugin: restrict the native libraries packaged into the APK to
// an explicit ABI list (e.g. `['arm64-v8a']`).
//
// Driven by the ANDROID_ABI env var via app.config.js, which EAS build
// profiles set per variant. When `abis` is empty/undefined the project is
// left untouched, producing the regular universal build.
//
// @expo/config-plugins is available transitively via the `expo` package.
const { withAppBuildGradle } = require('@expo/config-plugins');

const KNOWN_ABIS = new Set(['arm64-v8a', 'armeabi-v7a', 'x86_64']);

function toGroovyStringList(abis) {
  return abis.map((abi) => `'${abi}'`).join(', ');
}

function withAbiFilter(config, { abis } = {}) {
  if (!Array.isArray(abis) || abis.length === 0) {
    return config;
  }
  for (const abi of abis) {
    if (!KNOWN_ABIS.has(abi)) {
      throw new Error(
        `[withAbiFilter] Unknown ABI "${abi}". Expected one of: ${[...KNOWN_ABIS].join(', ')}.`
      );
    }
  }
  return withAppBuildGradle(config, (mod) => {
    const anchor = 'defaultConfig {';
    if (!mod.modResults.contents.includes(anchor)) {
      throw new Error('[withAbiFilter] Could not find `defaultConfig` in app/build.gradle.');
    }
    mod.modResults.contents = mod.modResults.contents.replace(
      anchor,
      `${anchor}\n        ndk {\n            abiFilters ${toGroovyStringList(abis)}\n        }`
    );
    return mod;
  });
}

module.exports = withAbiFilter;
