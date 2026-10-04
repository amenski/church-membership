<template>
  <div data-density="comfortable" class="flex min-h-screen items-center justify-center bg-mist px-4 py-10">
    <div class="max-w-lg text-center">
      <p class="m-0 font-ethiopic text-[length:clamp(2.5rem,9vw,4rem)] leading-tight font-bold text-teal">ፈለገ ሰላም</p>
      <h1 class="mt-0 mb-6 font-display text-[length:clamp(1.75rem,6vw,2.5rem)] leading-[1.2] font-bold text-ink">{{ $t('landing.title') }}</h1>
      <p class="mt-0 mb-6 text-lg">{{ $t('landing.tagline') }}</p>
      <BaseButton to="/login" size="lg">{{ $t('landing.signIn') }}</BaseButton>
      <p class="mt-6 mb-0 text-sm text-muted">{{ $t('landing.note') }}</p>
    </div>
  </div>
</template>

<script>
import BaseButton from '@/components/BaseButton.vue'

export default {
  name: 'LandingView',
  components: { BaseButton }
}
</script>
