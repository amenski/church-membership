<template>
  <!-- From lg the header is a full-width band, so the page's own padding (App.vue) is dropped here and the content area below carries it -->
  <div class="lg:max-w-none! lg:p-0!">
    <PageHead :title="$t('nav.activity')" :lead="$t('activity.lead')" band />

    <div class="lg:mx-auto lg:max-w-[1400px] lg:px-8 lg:pt-6 lg:pb-10">
    <!-- the band has no lead, so from lg the line sits here, as on the board -->
    <p class="mt-0 mb-4 hidden text-sm text-muted lg:block">{{ $t('activity.lead') }}</p>

    <AlertBanner v-if="loadError">
      <div class="flex flex-wrap items-center justify-between gap-3">
        <span>{{ $t('activity.loadError') }}</span>
        <BaseButton variant="secondary" size="sm" @click="load">{{ $t('common.tryAgain') }}</BaseButton>
      </div>
    </AlertBanner>

    <p v-if="!loaded" class="m-0 py-4 text-(length:--text-body) text-muted" role="status">{{ $t('activity.loading') }}</p>

    <!-- Filter -->
    <form v-if="entries.length" class="mb-6 grid grid-cols-1 gap-3 md:flex md:flex-wrap md:items-end lg:mb-4 lg:items-center lg:gap-x-4" role="search" :aria-label="$t('activity.filterAria')" @submit.prevent>
      <div class="md:w-64">
        <label for="filter-type" :class="[LABEL, 'lg:sr-only']">{{ $t('activity.show') }}</label>
        <select id="filter-type" v-model="type" :class="[CONTROL, 'lg:text-sm']">
          <option value="ALL">{{ $t('activity.allActivity') }}</option>
          <option v-for="item in ACTIVITY_TYPES" :key="item.value" :value="item.value">{{ $t(item.labelKey) }}</option>
        </select>
      </div>
      <TextButton v-if="type !== 'ALL' && visible.length" class="text-left md:py-1.5" @click="type = 'ALL'">{{ $t('activity.clearFilter') }}</TextButton>
    </form>

    <!-- Empty states -->
    <EmptyNote v-if="loaded && !loadError && !entries.length">{{ $t('activity.none') }}</EmptyNote>
    <div v-else-if="entries.length && !visible.length">
      <EmptyNote>{{ $t('activity.noneOfType', { type: $t(activityTypeKey(type)), n: entries.length }) }}<template v-if="canShowMore">{{ $t('activity.noneOfTypeMore') }}</template></EmptyNote>
      <BaseButton variant="secondary" class="mt-2" @click="type = 'ALL'">{{ $t('activity.clearFilter') }}</BaseButton>
    </div>

    <template v-if="visible.length">
      <p class="mt-0 mb-2 text-sm text-muted" aria-live="polite">
        {{ type === 'ALL' ? $t('activity.latestEntries', { n: entries.length, entry: $t('activity.entryWord', entries.length) }) : $t('activity.ofLatest', { shown: visible.length, total: entries.length }) }}
      </p>

      <!-- One ruled card per day; an entry is a grid row from md, stacked below -->
      <section :aria-label="$t('activity.logAria')" class="flex flex-col gap-4">
        <div v-for="day in days" :key="day.key" class="overflow-hidden rounded-md border border-rule bg-paper">
          <div class="flex items-baseline justify-between gap-3 border-b border-rule bg-mist px-4 py-2.5 md:px-5">
            <h2 class="m-0 text-sm font-semibold text-ink">{{ day.heading }}</h2>
            <span class="shrink-0 text-xs text-muted">{{ $t('activity.entryWord', day.entries.length) }}</span>
          </div>
          <ul class="m-0 list-none p-0">
            <li
              v-for="entry in day.entries"
              :key="entry.id"
              class="flex min-h-(--row-h) flex-col gap-1 border-b border-rule px-4 py-3 last:border-b-0 md:grid md:grid-cols-[56px_176px_minmax(0,1fr)_240px] md:items-center md:gap-x-4 md:px-5 md:py-2.5"
            >
              <div class="flex items-center gap-3 md:contents">
                <span class="text-sm text-muted tabular-nums">{{ time(entry) }}</span>
                <span><StatusBadge :tone="activityTone(entry.type)">{{ $t(activityTypeKey(entry.type)) }}</StatusBadge></span>
              </div>
              <div class="[overflow-wrap:anywhere]">{{ entry.description }}</div>
              <div class="text-sm text-muted [overflow-wrap:anywhere]">{{ actorLabel(entry.actor, $t) }}</div>
            </li>
          </ul>
        </div>
      </section>
    </template>

    <div v-if="entries.length" class="mt-6">
      <BaseButton v-if="canShowMore" variant="secondary" :disabled="loadingMore" :aria-busy="loadingMore ? 'true' : undefined" class="max-sm:w-full" @click="showMore">
        {{ loadingMore ? $t('activity.loadingMore') : $t('activity.showMore') }}
      </BaseButton>
      <p v-else-if="entries.length >= MAX_LIMIT" ref="endNote" tabindex="-1" class="m-0 text-sm text-muted">{{ $t('activity.atMost', { n: MAX_LIMIT }) }}</p>
      <p v-else ref="endNote" tabindex="-1" class="m-0 text-sm text-muted">{{ $t('activity.everything') }}</p>
    </div>
    </div>
  </div>
</template>

<script>
import { useI18n } from 'vue-i18n'
import api from '@/services/api'
import { formatDate } from '@/utils'
import { ACTIVITY_TYPES, MAX_LIMIT, PAGE_SIZE, actorLabel, activityTone, activityTypeKey, filterByType, groupByDay, nextLimit } from '@/utils/activityLog'
import AlertBanner from '@/components/AlertBanner.vue'
import BaseButton from '@/components/BaseButton.vue'
import EmptyNote from '@/components/EmptyNote.vue'
import PageHead from '@/components/PageHead.vue'
import StatusBadge from '@/components/StatusBadge.vue'
import TextButton from '@/components/TextButton.vue'

import { CONTROL, LABEL } from '@/ui/classes'

export default {
  name: 'ActivityView',
  components: { AlertBanner, BaseButton, EmptyNote, PageHead, StatusBadge, TextButton },
  setup() {
    const { t } = useI18n()
    return { ACTIVITY_TYPES, MAX_LIMIT, actorLabel, activityTone, activityTypeKey, t, LABEL, CONTROL }
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
    days() {
      return groupByDay(this.visible, this.t)
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
    time(entry) {
      return formatDate(entry.createdAt, 'HH:mm')
    }
  }
}
</script>
