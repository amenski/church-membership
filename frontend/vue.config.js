const { defineConfig } = require('@vue/cli-service')
module.exports = defineConfig({
    transpileDependencies: true,
    // Proxy API requests to our Spring Boot backend during development
    devServer: {
        port: 8081,
        proxy: {
            '/api': {
                target: 'http://localhost:8080',
                ws: true,
                changeOrigin: true
            }
        }
    },
    // Output to dist/ directory for Gradle build integration
    outputDir: 'dist',
    // Configure public path for proper asset loading
    publicPath: '/static/'
})
