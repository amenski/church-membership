<template>
  <div
    v-if="segments > 0"
    class="dues-meter"
    role="img"
    :aria-label="`${paid} of ${total} active members are paid up`"
    :style="{ '--n': segments, '--rest': `${rest}%` }"
  >
    <div class="dues-meter__track" aria-hidden="true">
      <span v-for="i in segments" :key="i" class="dues-meter__cell"></span>
      <div v-if="filled > 0" class="dues-meter__fill">
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

<style scoped>
.dues-meter__track {
  position: relative;
  display: flex;
  margin-right: -3px; /* the last segment's gap hangs outside, so cells and mask line up */
}

.dues-meter__cell {
  flex: 1 1 0;
  height: 20px;
  margin-right: 3px;
  border: 1px solid var(--felege-ochre-edge);
  border-radius: 2px;
  background: var(--felege-ochre-tint);
}

/* One continuous woven strip, cut into segments by a mask and into "paid" by a clip */
.dues-meter__fill {
  position: absolute;
  inset: 0;
  --cell: calc(100% / var(--n));
  -webkit-mask-image: repeating-linear-gradient(90deg, #000 0, #000 calc(var(--cell) - 3px), transparent calc(var(--cell) - 3px), transparent var(--cell));
  mask-image: repeating-linear-gradient(90deg, #000 0, #000 calc(var(--cell) - 3px), transparent calc(var(--cell) - 3px), transparent var(--cell));
  clip-path: inset(0 var(--rest) 0 0);
  animation: dues-sweep 700ms ease-out both;
}

@keyframes dues-sweep {
  from { clip-path: inset(0 100% 0 0); }
}

@media (prefers-reduced-motion: reduce) {
  .dues-meter__fill { animation: none; }
}
</style>
