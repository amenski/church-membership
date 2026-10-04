<template>
  <button
    type="button"
    class="cursor-pointer border-0 bg-transparent px-0 py-2 font-medium normal-nums whitespace-nowrap text-teal underline underline-offset-[3px] hover:text-teal-hover disabled:cursor-default disabled:text-muted disabled:no-underline"
  >
    <slot />
  </button>
</template>

<script>
// Plain teal text button for row actions ("Send reminder")
export default { name: 'TextButton' }
</script>
