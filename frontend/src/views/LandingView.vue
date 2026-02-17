<template>
  <div class="landing-page">
    <!-- Hero Section -->
    <div class="hero-section bg-primary text-white py-5">
      <div class="container">
        <div class="row align-items-center">
          <div class="col-lg-6">
            <h1 class="display-4 fw-bold">{{ $t('landing.title') }}</h1>
            <p class="lead mb-4">{{ $t('landing.subtitle') }}</p>
            <div class="d-flex gap-3">
              <router-link to="/login" class="btn btn-light btn-lg">
                <i class="bi bi-box-arrow-in-right me-2"></i>
                {{ $t('landing.signIn') }}
              </router-link>
              <router-link to="/register" class="btn btn-outline-light btn-lg">
                <i class="bi bi-person-plus me-2"></i>
                {{ $t('landing.register') }}
              </router-link>
            </div>
          </div>
          <div class="col-lg-6 text-center">
            <i class="bi bi-people display-1"></i>
          </div>
        </div>
      </div>
    </div>

    <!-- Features Section -->
    <div class="features-section py-5">
      <div class="container">
        <h2 class="text-center mb-5">{{ $t('landing.features') }}</h2>
        <div class="row g-4">
          <div class="col-md-4">
            <div class="card h-100 border-0 shadow-sm">
              <div class="card-body text-center">
                <div class="feature-icon mb-3">
                  <i class="bi bi-people display-5 text-primary"></i>
                </div>
                <h5 class="card-title">{{ $t('landing.memberManagement') }}</h5>
                <p class="card-text text-muted">
                  {{ $t('landing.memberManagementDesc') }}
                </p>
              </div>
            </div>
          </div>
          <div class="col-md-4">
            <div class="card h-100 border-0 shadow-sm">
              <div class="card-body text-center">
                <div class="feature-icon mb-3">
                  <i class="bi bi-currency-dollar display-5 text-success"></i>
                </div>
                <h5 class="card-title">{{ $t('landing.paymentTracking') }}</h5>
                <p class="card-text text-muted">
                  {{ $t('landing.paymentTrackingDesc') }}
                </p>
              </div>
            </div>
          </div>
          <div class="col-md-4">
            <div class="card h-100 border-0 shadow-sm">
              <div class="card-body text-center">
                <div class="feature-icon mb-3">
                  <i class="bi bi-envelope display-5 text-info"></i>
                </div>
                <h5 class="card-title">{{ $t('landing.communications') }}</h5>
                <p class="card-text text-muted">
                  {{ $t('landing.communicationsDesc') }}
                </p>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- Footer -->
    <footer class="bg-dark text-white py-4">
      <div class="container text-center">
        <p class="mb-0">&copy; {{ new Date().getFullYear() }} {{ appTitle }}. All rights reserved.</p>
      </div>
    </footer>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useAppStore } from '@/stores/appStore'

const appStore = useAppStore()
const appTitle = computed(() => appStore.appTitle)
</script>

<style scoped>
.landing-page {
  min-height: 100vh;
}

.hero-section {
  min-height: 60vh;
  display: flex;
  align-items: center;
}

.feature-icon {
  width: 80px;
  height: 80px;
  margin: 0 auto;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background-color: var(--bs-light);
}

.features-section {
  background-color: #f8f9fa;
}
</style>
