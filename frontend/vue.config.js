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
    // Output to Spring Boot's static resources directory when building
    outputDir: '../src/main/resources/static'
})
