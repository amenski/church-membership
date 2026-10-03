<template>
  <div
    v-if="segments > 0"
    role="img"
    :aria-label="`${paid} of ${total} active members are paid up`"
    :style="{ '--n': segments, '--rest': `${rest}%` }"
  >
    <div class="tw:relative tw:-mr-[3px] tw:flex" aria-hidden="true">
      <!-- the last segment's gap hangs outside (-mr), so cells and mask line up -->
      <span
        v-for="i in segments"
        :key="i"
        class="tw:mr-[3px] tw:h-5 tw:flex-1 tw:rounded-[2px] tw:border tw:border-ochre-edge tw:bg-ochre-tint"
      ></span>
      <!-- One continuous woven strip, cut into segments by a mask and into "paid" by a clip -->
      <div
        v-if="filled > 0"
        class="tw:absolute tw:inset-0 tw:[--cell:calc(100%/var(--n))] tw:[clip-path:inset(0_var(--rest)_0_0)] tw:[mask-image:repeating-linear-gradient(90deg,#000_0,#000_calc(var(--cell)-3px),transparent_calc(var(--cell)-3px),transparent_var(--cell))] tw:motion-safe:animate-weave"
      >
        <WovenBand :height="20" />
      </div>
    </div>
  </div>
</template>

<script>
import WovenBand from '@/components/WovenBand.vue'
import { meterSegments } from '@/utils/dashboardMeter'

export default {
  name: 'DuesMeter',
  components: { WovenBand },
  props: {
    total: { type: Number, required: true },
    paid: { type: Number, required: true }
  },
  computed: {
    meter() {
      return meterSegments(this.total, this.paid)
    },
    segments() {
      return this.meter.segments
    },
    filled() {
      return this.meter.filled
    },
    rest() {
      return (1 - this.filled / this.segments) * 100
    }
  }
}
</script>
