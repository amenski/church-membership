<template>
  <button
    type="button"
    class="tw:cursor-pointer tw:border-0 tw:bg-transparent tw:px-0 tw:py-2 tw:font-medium tw:whitespace-nowrap tw:text-teal tw:underline tw:underline-offset-[3px] tw:hover:text-teal-hover tw:disabled:cursor-default tw:disabled:text-muted tw:disabled:no-underline"
  >
    <slot />
  </button>
</template>

<script>
// Plain teal text button for row actions ("Send reminder")
export default { name: 'TextButton' }
</script>
