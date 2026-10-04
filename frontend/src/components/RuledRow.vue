<template>
  <!-- Fixed 56px on purpose: used only by the Overview, which keeps its own sizes (docs/design.md) -->
  <li class="flex min-h-14 items-center gap-4 border-b border-rule py-2 tabular-nums max-sm:flex-wrap max-sm:gap-y-0"><slot /></li>
</template>

<script>
export default { name: 'RuledRow' }
</script>
