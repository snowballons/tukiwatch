// Dynamic Expo config overlay, merged over app.json (which stays the source
// of truth for everything static, including the version stamped by CI).
//
// Reads ANDROID_ABI (comma-separated, set per EAS build profile) to restrict
// the packaged native libraries, e.g. ANDROID_ABI=arm64-v8a. Unset/empty
// means a regular universal build.
module.exports = function appConfig({ config }) {
  const abis = (process.env.ANDROID_ABI || '')
    .split(',')
    .map((s) => s.trim())
    .filter(Boolean);

  return {
    ...config,
    plugins: [
      ...(config.plugins ?? []),
      'expo-secure-store',
      ['./plugins/withAbiFilter', { abis }],
    ],
  };
};
