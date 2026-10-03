<template>
  <div data-density="comfortable" class="tw:flex tw:min-h-screen tw:items-center tw:justify-center tw:bg-mist tw:px-4 tw:py-10">
    <div class="tw:max-w-lg tw:text-center">
      <p class="tw:m-0 tw:font-ethiopic tw:text-[length:clamp(2.5rem,9vw,4rem)] tw:leading-tight tw:font-bold tw:text-teal">ፈለገ ሰላም</p>
      <h1 class="tw:mt-0 tw:mb-6 tw:font-display tw:text-[length:clamp(1.75rem,6vw,2.5rem)] tw:leading-[1.2] tw:font-bold tw:text-ink">{{ $t('landing.title') }}</h1>
      <p class="tw:mt-0 tw:mb-6 tw:text-lg">{{ $t('landing.tagline') }}</p>
      <BaseButton to="/login" size="lg">{{ $t('landing.signIn') }}</BaseButton>
      <p class="tw:mt-6 tw:mb-0 tw:text-sm tw:text-muted">{{ $t('landing.note') }}</p>
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
