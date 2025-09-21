const CompressionPlugin = require('compression-webpack-plugin')
const path = require('path')
const TerserPlugin = require('terser-webpack-plugin')
const { WebpackManifestPlugin } = require('webpack-manifest-plugin')
const { WebpackBundleAnalyzer } = require('webpack-bundle-analyzer')

// 设置网站默认标题（实际运行时会从数据库获取）
const siteTitle = '';

module.exports = {
  devServer: {
    port: 80,
    https: false,
    open: false
  },
  lintOnSave: false,
  productionSourceMap: false,
  chainWebpack: config => {
    // 禁用默认的预加载和预取，减少不必要的资源
    config.plugins.delete('prefetch')
    config.plugins.delete('preload')
    
    config
      .plugin('html')
      .tap(args => {
        args[0].title = siteTitle
        // 使用默认的自动注入
        args[0].inject = true
        // 启用 HTML 压缩
        if (process.env.NODE_ENV === 'production') {
          args[0].minify = {
            removeComments: true,
            collapseWhitespace: true,
            removeAttributeQuotes: true,
            collapseBooleanAttributes: true,
            removeScriptTypeAttributes: true,
            removeStyleLinkTypeAttributes: true,
            minifyCSS: true,
            minifyJS: true,
            removeRedundantAttributes: true,
            useShortDoctype: true,
            removeEmptyAttributes: true,
            removeOptionalTags: false,
            minifyURLs: true
          }
        }
        return args
      })

    config.optimization.splitChunks({
      cacheGroups: {
        // 将node_modules中的模块打包到vendors~app.js中
        vendors: {
          name: 'chunk-vendors',
          test: /[\\/]node_modules[\\/]/,
          chunks: 'initial',
          priority: 10,
          reuseExistingChunk: true,
          enforce: true
        },
        // 将element-ui单独打包
        elementUI: {
          name: 'chunk-elementUI',
          priority: 20, // 权重必须大于 vendors
          test: /[\\/]node_modules[\\/]_?element-ui(.*)/,
          chunks: 'all'
        },
        // 将highlight.js单独打包
        highlightJS: {
          name: 'chunk-highlightJS',
          priority: 20,
          test: /[\\/]node_modules[\\/]_?highlight.js(.*)/,
          chunks: 'all'
        }
      }
    })

    // 启用打包分析工具
    if (process.env.npm_config_report) {
      config.plugin('bundle-analyzer').use(WebpackBundleAnalyzer)
    }
  },
  configureWebpack: {
    plugins: [
      new CompressionPlugin({
        algorithm: 'gzip',
        test: /\.js$|\.html$|\.css$/,
        filename: '[path].gz[query]',
        minRatio: 0.8,
        threshold: 8192,
        deleteOriginalAssets: false
      }),
      new WebpackManifestPlugin({
        fileName: 'manifest.json',
        publicPath: '/',
        filter: (file) => !file.path.endsWith('.gz') // 不包含压缩文件
      })
    ],
    resolve: {
      alias: {
        '@': path.resolve(__dirname, 'src'),
        'static': path.resolve(__dirname, 'public')
      }
    },
    optimization: {
      minimize: true,
      minimizer: [
        new TerserPlugin({
          terserOptions: {
            compress: {
              drop_console: true,
              drop_debugger: true
            }
          }
        })
      ],
      splitChunks: {
        chunks: 'all',
        maxInitialRequests: Infinity,
        minSize: 20000,
        cacheGroups: {
          vendor: {
            test: /[\\/]node_modules[\\/]/,
            name(module) {
              const packageName = module.context.match(/[\\/]node_modules[\\/](.*?)([\\/]|$)/)[1];
              return `npm.${packageName.replace('@', '')}`;
            }
          }
        }
      }
    },
    externals: {
      // 'vue': 'Vue',
      // 'vue-router': 'VueRouter',
      // 'element-ui': 'ELEMENT',
      // 'highlight.js': 'hljs',
      // 'jquery': '$'
    }
  },
  publicPath: '/',
  outputDir: 'dist',
  assetsDir: 'static'
}
