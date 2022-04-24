import typescript from 'rollup-plugin-typescript2';
import postcss from 'rollup-plugin-postcss'

let module = "build/generated/resources/de/mnl/ahp/conlets/management/Admin-functions.js"

let pathsMap = {
    "vue": "../../page-resource/vue/vue.esm-browser.js",
    "chartjs": "../../page-resource/chart.js/dist/chart.esm.js",
    "jgconsole": "../../console-base-resource/jgconsole.js",
    "aash-plugin": "../../page-resource/aash-vue-components/lib/aash-vue-components.js",
    "jgwc": "../../page-resource/jgwc-vue-components/jgwc-components.js",
    "l10nBundles": "./Admin-l10nBundles.ftl.js"
}

export default {
  external: ['vue', 'aash-plugin', 'jgconsole', 'jgwc', 'l10nBundles'],
  input: "src/de/mnl/ahp/conlets/management/browser/Admin-functions.ts",
  output: [
    {
      format: "esm",
      file: module,
      sourcemap: true,
      sourcemapPathTransform: (relativeSourcePath, _sourcemapPath) => {
        return relativeSourcePath.replace(/^([^/]*\/){12}/, "./");
      },
      paths: pathsMap
    }
  ],
  plugins: [
    typescript(),
    postcss()
  ]
};
