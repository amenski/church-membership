<template>
  <div>
    <PageHead title="Activity" lead="Who changed or exported what, newest first. Only administrators can see this." />

    <AlertBanner v-if="loadError">
      <div class="flex flex-wrap items-center justify-between gap-3">
        <span>The activity did not load. Check your connection and try again.</span>
        <BaseButton variant="secondary" size="sm" @click="load">Try again</BaseButton>
      </div>
    </AlertBanner>

    <p v-if="!loaded" class="m-0 py-4 text-(length:--text-body) text-muted" role="status">Loading activity...</p>

    <!-- Filter -->
    <form v-if="entries.length" class="mb-6 grid grid-cols-1 gap-3 md:flex md:flex-wrap md:items-end" role="search" aria-label="Filter activity" @submit.prevent>
      <div class="md:w-64">
        <label for="filter-type" :class="LABEL">Show</label>
        <select id="filter-type" v-model="type" :class="CONTROL">
          <option value="ALL">All activity</option>
          <option v-for="item in ACTIVITY_TYPES" :key="item.value" :value="item.value">{{ item.label }}</option>
        </select>
      </div>
      <TextButton v-if="type !== 'ALL' && visible.length" class="text-left md:py-1.5" @click="type = 'ALL'">Clear filter</TextButton>
    </form>

    <!-- Empty states -->
    <EmptyNote v-if="loaded && !loadError && !entries.length">No activity recorded yet.</EmptyNote>
    <div v-else-if="entries.length && !visible.length">
      <EmptyNote>No {{ activityTypeLabel(type).toLowerCase() }} entries in the latest {{ entries.length }}.<template v-if="canShowMore"> Show more to look further back.</template></EmptyNote>
      <BaseButton variant="secondary" class="mt-2" @click="type = 'ALL'">Clear filter</BaseButton>
    </div>

    <template v-if="visible.length">
      <p class="mt-0 mb-2 text-sm text-muted" aria-live="polite">
        {{ type === 'ALL' ? `Latest ${entries.length} ${entries.length === 1 ? 'entry' : 'entries'}` : `${visible.length} of the latest ${entries.length} entries` }}
      </p>

      <!-- md and up: ruled table -->
      <table :class="TABLE">
        <caption class="sr-only">Activity log, newest first</caption>
        <thead>
          <tr class="border-b border-rule">
            <th scope="col" :class="TH">When</th>
            <th scope="col" :class="TH">Who</th>
            <th scope="col" :class="TH">What happened</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="entry in visible" :key="entry.id" class="min-h-(--row-h) border-b border-rule">
            <td :class="[TD, 'whitespace-nowrap']">{{ when(entry) }}</td>
            <td :class="[TD, 'max-w-0 w-[28%] [overflow-wrap:anywhere]']">{{ actorLabel(entry.actor) }}</td>
            <td :class="[TD, 'max-w-0 w-[44%] [overflow-wrap:anywhere]']">{{ entry.description }}</td>
          </tr>
        </tbody>
      </table>

      <!-- Below md: the same rows, stacked -->
      <ul class="m-0 list-none border-t border-rule p-0 md:hidden">
        <li v-for="entry in visible" :key="entry.id" class="border-b border-rule py-3">
          <div class="[overflow-wrap:anywhere]">{{ entry.description }}</div>
          <div class="text-sm text-muted tabular-nums">{{ when(entry) }}</div>
          <div class="text-sm text-muted [overflow-wrap:anywhere]">{{ actorLabel(entry.actor) }}</div>
        </li>
      </ul>
    </template>

    <div v-if="entries.length" class="mt-6">
      <BaseButton v-if="canShowMore" variant="secondary" :disabled="loadingMore" :aria-busy="loadingMore ? 'true' : undefined" class="max-sm:w-full" @click="showMore">
        {{ loadingMore ? 'Loading...' : 'Show more' }}
      </BaseButton>
      <p v-else-if="entries.length >= MAX_LIMIT" ref="endNote" tabindex="-1" class="m-0 text-sm text-muted">This is the most the screen shows: the latest {{ MAX_LIMIT }} entries.</p>
      <p v-else ref="endNote" tabindex="-1" class="m-0 text-sm text-muted">That is everything recorded so far.</p>
    </div>
  </div>
</template>

<script>
import api from '@/services/api'
import { formatDate } from '@/utils'
import { ACTIVITY_TYPES, MAX_LIMIT, PAGE_SIZE, actorLabel, activityTypeLabel, filterByType, nextLimit } from '@/utils/activityLog'
import AlertBanner from '@/components/AlertBanner.vue'
import BaseButton from '@/components/BaseButton.vue'
import EmptyNote from '@/components/EmptyNote.vue'
import PageHead from '@/components/PageHead.vue'
import TextButton from '@/components/TextButton.vue'

import { CONTROL, LABEL, TABLE, TABLE_TH as TH, TABLE_TD as TD } from '@/ui/classes'

export default {
  name: 'ActivityView',
  components: { AlertBanner, BaseButton, EmptyNote, PageHead, TextButton },
  setup() {
    return { ACTIVITY_TYPES, MAX_LIMIT, actorLabel, activityTypeLabel, TABLE, LABEL, CONTROL, TH, TD }
  },
  data() {
    return {
      entries: [],
      limit: PAGE_SIZE,
      type: 'ALL',
      loaded: false,
      loadError: false,
      loadingMore: false
    }
  },
  computed: {
    visible() {
      return filterByType(this.entries, this.type)
    },
    // The server returns at most `limit` entries, so a full page means there may be more
    canShowMore() {
      return this.limit < MAX_LIMIT && this.entries.length >= this.limit
    }
  },
  async created() {
    await this.load()
  },
  methods: {
    async load() {
      this.loadError = false
      try {
        const entries = await api.getActivityLog(this.limit)
        this.entries = Array.isArray(entries) ? entries : []
      } catch (error) {
        console.error('Error loading activity:', error)
        this.loadError = true
      } finally {
        this.loaded = true
      }
    },
    async showMore() {
      const previous = this.limit
      this.limit = nextLimit(previous)
      this.loadingMore = true
      try {
        await this.load()
      } finally {
        if (this.loadError) this.limit = previous
        this.loadingMore = false
      }
      // The button is gone when everything is shown: keep the keyboard user's place
      await this.$nextTick()
      if (!this.canShowMore) this.$refs.endNote?.focus()
    },
    when(entry) {
      return formatDate(entry.createdAt, 'MMM d, yyyy, h:mm a')
    }
  }
}
</script>
