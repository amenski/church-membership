<template>
  <main class="landing">
    <div class="landing__inner">
      <p class="landing__geez">ፈለገ ሰላም</p>
      <h1 class="landing__latin">{{ $t('landing.title') }}</h1>
      <p class="landing__tagline">{{ $t('landing.tagline') }}</p>
      <router-link to="/login" class="btn btn-primary btn-lg">{{ $t('landing.signIn') }}</router-link>
      <p class="landing__note">{{ $t('landing.note') }}</p>
    </div>
  </main>
</template>

<style scoped>
.landing {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: var(--space-6) var(--space-4);
  background: var(--felege-mist);
}
.landing__inner {
  max-width: 32rem;
  text-align: center;
}
.landing__geez {
  margin: 0;
  font-family: var(--felege-font-geez);
  font-weight: 700;
  font-size: clamp(2.5rem, 9vw, 4rem);
  line-height: 1.25;
  color: var(--felege-teal);
}
.landing__latin {
  margin: 0 0 var(--space-5);
  font-size: clamp(1.75rem, 6vw, 2.5rem);
}
.landing__tagline {
  margin: 0 0 var(--space-5);
  font-size: 1.125rem;
}
.landing__note {
  margin: var(--space-5) 0 0;
  font-size: 0.875rem;
  color: var(--felege-muted);
}
</style>
