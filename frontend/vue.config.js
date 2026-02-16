import { defineConfig } from '@vue/cli-service'
import path from 'path'
import { fileURLToPath } from 'url'

const __filename = fileURLToPath(import.meta.url)
const __dirname = path.dirname(__filename)

export default defineConfig({
  transpileDependencies: true,

  // Production optimizations
  productionSourceMap: false,

  // Webpack configuration
  configureWebpack: {
    resolve: {
      alias: {
        '@': path.resolve(__dirname, 'src')
      },
      extensions: ['.js', '.vue', '.json']
    },
    optimization: {
      splitChunks: {
        chunks: 'all',
        cacheGroups: {
          vendor: {
            test: /[\\/]node_modules[\\/]/,
            name: 'vendors',
            priority: 20,
            chunks: 'all'
          }
        }
      }
    },
    performance: {
      maxEntrypointSize: 512000,
      maxAssetSize: 512000
    }
  },

  // Webpack chain configuration
  chainWebpack: config => {
    // HTML plugin configuration
    config.plugin('html').tap(args => {
      args[0].title = process.env.VITE_APP_TITLE || 'Member Tracker Admin'
      args[0].meta = {
        viewport: 'width=device-width,initial-scale=1.0',
        'theme-color': '#2c3e50',
        description: 'Admin backoffice for managing church members, payments, and communications'
      }
      return args
    })

    // Image optimization
    config.module
      .rule('images')
      .use('url-loader')
      .loader('url-loader')
      .tap(options => {
        const newOptions = {
          ...options,
          limit: 8192
        }
        // Only add fallback if options exist
        if (options && options.fallback) {
          newOptions.fallback = {
            ...options.fallback,
            options: {
              name: 'img/[name].[hash:8].[ext]'
            }
          }
        }
        return newOptions
      })
  },

  // CSS configuration
  css: {
    extract: true,
    sourceMap: process.env.NODE_ENV !== 'production'
  },

  // Development server configuration
  devServer: {
    port: 8081,
    host: 'localhost',
    hot: true,
    open: false,
    client: {
      overlay: {
        warnings: false,
        errors: true
      }
    },
    proxy: {
      '/api': {
        target: 'http://localhost:8082',
        changeOrigin: true
      }
    }
  },

  // Output configuration
  outputDir: 'dist',
  publicPath: process.env.NODE_ENV === 'production' ? '/static/' : '/',
  assetsDir: 'assets',
  filenameHashing: true,

  // Linting
  lintOnSave: process.env.NODE_ENV !== 'production'
})
