<template>
  <!-- From lg the header is a full-width band, so the page's own padding (App.vue) is dropped here and the content area below carries it -->
  <div class="lg:max-w-none! lg:p-0!">
    <PageHead :title="$t('nav.members')" compact band>
      <template v-if="authStore.isStaff" #actions>
        <!-- below lg Export CSV sits in the count line under the filters -->
        <BaseButton variant="secondary" class="max-lg:hidden" @click="exportMembers">
          <Icon name="download" :size="16" class="mr-1.5" />{{ $t('common.exportCsv') }}
        </BaseButton>
        <BaseButton class="max-lg:min-h-11" @click="showAddModal">
          <Icon name="plus" :size="16" class="mr-1.5" />{{ $t('members.addMember') }}
        </BaseButton>
      </template>
    </PageHead>

    <div class="lg:mx-auto lg:max-w-[1400px] lg:px-8 lg:pt-6 lg:pb-10">
    <AlertBanner v-if="loadError">
      <div class="flex flex-wrap items-center justify-between gap-3">
        <span>{{ $t('members.loadError') }}</span>
        <BaseButton variant="secondary" size="sm" @click="loadMembers">{{ $t('common.tryAgain') }}</BaseButton>
      </div>
    </AlertBanner>

    <!-- Filters. From lg one row: the status control on the left; Dues, Sort by, search and "More filters" (the date pair, in a
         popover so it never takes a row) on the right, 32px high, labels visually hidden. On a phone or tablet: status, then
         search beside one "Filters" button that opens Dues, Sort by and the date pair; closed until asked for -->
    <form v-if="members.length || showingArchived" class="relative mb-3 grid grid-cols-[minmax(0,1fr)_auto] gap-3 lg:mb-4 lg:flex lg:flex-wrap lg:items-center lg:gap-x-4" role="search" :aria-label="$t('members.filterAria')" @submit.prevent>
      <div class="col-span-2 overflow-x-auto lg:order-1 lg:overflow-visible">
        <div role="group" :aria-label="$t('members.filterByStatus')" class="inline-flex">
          <button
            v-for="segment in statusSegments"
            :key="segment.value"
            type="button"
            :class="[SEGMENT, segmentShape(segment.value), filters.status === segment.value ? 'z-10 border-teal bg-teal text-paper hover:bg-teal-hover' : 'border-field bg-paper text-ink hover:bg-teal-tint']"
            :aria-pressed="filters.status === segment.value ? 'true' : 'false'"
            @click="filters.status = segment.value"
          >
            {{ $t(segment.labelKey) }}<template v-if="segment.count !== null"> <span class="tabular-nums">{{ segment.count }}</span></template>
          </button>
        </div>
      </div>
      <div class="relative lg:order-5 lg:max-w-60 lg:flex-[1_1_10rem]">
        <label for="filter-search" class="sr-only">{{ $t('common.search') }}</label>
        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" aria-hidden="true" focusable="false" class="pointer-events-none absolute top-1/2 left-2.5 -translate-y-1/2 text-muted max-lg:hidden"><circle cx="11" cy="11" r="6.5" /><path d="M16 16l4.5 4.5" /></svg>
        <input id="filter-search" v-model="filters.search" type="search" :placeholder="$t('members.searchPlaceholder')" autocomplete="off" :class="[CONTROL, 'max-lg:h-11 lg:pl-8 lg:text-sm']">
      </div>
      <button
        type="button"
        :class="['flex h-11 cursor-pointer items-center gap-1.5 rounded-sm border px-3 text-base font-medium whitespace-nowrap lg:hidden', panelFilterCount ? 'border-teal bg-teal-tint text-teal' : 'border-field bg-paper text-ink hover:border-teal hover:bg-teal-tint']"
        aria-controls="filter-panel"
        :aria-expanded="filtersOpen ? 'true' : 'false'"
        @click="filtersOpen = !filtersOpen"
      >
        {{ $t('members.filters') }}<template v-if="panelFilterCount"> ({{ panelFilterCount }})</template>
        <Icon :name="filtersOpen ? 'chevron-up' : 'chevron-down'" :size="16" />
      </button>
      <div id="filter-panel" :class="filtersOpen ? 'col-span-2 grid grid-cols-2 gap-3 lg:contents' : 'hidden lg:contents'">
        <div class="lg:order-3 lg:ml-auto lg:w-28">
          <label for="filter-dues" :class="[LABEL, 'lg:sr-only']">{{ $t('members.dues') }}</label>
          <select id="filter-dues" v-model="filters.paymentStatus" :class="[CONTROL, 'lg:text-sm']">
            <option value="ALL">{{ $t('members.duesAll') }}</option>
            <option value="CURRENT">{{ $t('members.duesCurrent') }}</option>
            <option value="OVERDUE">{{ $t('members.duesOverdue') }}</option>
          </select>
        </div>
        <div class="lg:order-4 lg:w-36">
          <label for="filter-sort" :class="[LABEL, 'lg:sr-only']">{{ $t('members.sortBy') }}</label>
          <select id="filter-sort" :value="sort.key" :class="[CONTROL, 'lg:text-sm']" @change="setSortOption($event.target.value)">
            <option v-for="option in SORT_OPTIONS" :key="option.key" :value="option.key">{{ $t(option.labelKey) }}</option>
          </select>
        </div>
        <!-- the date pair: in the panel below lg; from lg a popover under the row, opened by "More filters" -->
        <div id="filter-dates" role="group" :aria-label="$t('members.joinedBetween')" :class="['col-span-2 grid grid-cols-2 gap-3', moreOpen ? 'lg:absolute lg:top-full lg:right-0 lg:z-40 lg:mt-1 lg:w-80 lg:rounded-md lg:border lg:border-rule lg:bg-paper lg:p-3 lg:shadow-modal' : 'lg:hidden']" @keydown.esc="closeMore">
          <div>
            <label for="filter-from" :class="LABEL">{{ $t('members.joinedFrom') }}</label>
            <input id="filter-from" v-model="filters.joinedFrom" type="date" :class="[CONTROL, 'lg:text-sm']">
          </div>
          <div>
            <label for="filter-to" :class="LABEL">{{ $t('members.joinedTo') }}</label>
            <input id="filter-to" v-model="filters.joinedTo" type="date" :class="[CONTROL, 'lg:text-sm']">
          </div>
        </div>
      </div>
      <button
        ref="moreButton"
        type="button"
        :class="['hidden h-(--control-h) cursor-pointer items-center gap-1.5 rounded-sm border px-3 text-sm font-medium whitespace-nowrap lg:order-6 lg:flex', dateFilterCount ? 'border-teal bg-teal-tint text-teal' : 'border-field bg-paper text-ink hover:border-teal hover:bg-teal-tint']"
        aria-controls="filter-dates"
        :aria-expanded="moreOpen ? 'true' : 'false'"
        @click="moreOpen = !moreOpen"
        @keydown.esc="closeMore"
      >
        {{ $t('members.moreFilters') }}<template v-if="dateFilterCount"> ({{ dateFilterCount }})</template>
        <Icon :name="moreOpen ? 'chevron-up' : 'chevron-down'" :size="16" />
      </button>
      <TextButton v-if="hasActiveFilters && filteredMembers.length" class="col-span-2 text-left max-lg:min-h-11 lg:order-7 lg:col-auto lg:py-1.5" @click="clearFilters">{{ $t('common.clearFilters') }}</TextButton>
    </form>

    <!-- Empty states -->
    <div v-if="showingArchived && !source.length">
      <p v-if="!archivedLoaded" class="m-0 py-4 text-(length:--text-body) text-muted" role="status">{{ $t('members.loadingArchived') }}</p>
      <EmptyNote v-else>{{ $t('members.noArchived') }}</EmptyNote>
      <BaseButton variant="secondary" class="mt-2" @click="clearFilters">{{ $t('members.backToAll') }}</BaseButton>
    </div>
    <div v-else-if="loaded && !loadError && !members.length">
      <EmptyNote>{{ $t('members.empty') }}</EmptyNote>
      <BaseButton v-if="authStore.isStaff" class="mt-2" @click="showAddModal">{{ $t('members.addMember') }}</BaseButton>
    </div>
    <div v-else-if="source.length && !filteredMembers.length">
      <EmptyNote>{{ $t('members.noMatch') }}</EmptyNote>
      <BaseButton variant="secondary" class="mt-2" @click="clearFilters">{{ $t('common.clearFilters') }}</BaseButton>
    </div>

    <template v-if="filteredMembers.length">
      <div class="mb-2 flex items-center justify-between gap-3 lg:mb-0">
        <!-- from lg the visible count is the table card's footer; this line stays for screen readers -->
        <p class="m-0 text-sm text-muted lg:sr-only" aria-live="polite">
          {{ countText }}
        </p>
        <!-- below lg only: from lg Export CSV is in the page header -->
        <TextButton v-if="authStore.isStaff" class="-my-2.5 min-h-11 lg:hidden" @click="exportMembers">{{ $t('common.exportCsv') }}</TextButton>
      </div>

      <!-- Selection (STAFF+): the live region speaks the count, the bar holds what can be done with those members.
           From lg the bar floats: fixed 16px from the bottom of the viewport, centred over the content area right of the 232px rail
           (z 1050: above the table, below the row menu 1100 and dialogs 1200), and takes no room in the page. Below lg it is sticky above the cards -->
      <p class="sr-only" role="status" aria-live="polite">{{ selectionAnnouncement }}</p>
      <div v-if="canSelect" class="contents lg:pointer-events-none lg:fixed lg:right-0 lg:bottom-4 lg:left-[232px] lg:z-[1050] lg:block lg:px-6">
        <Transition enter-active-class="lg:transition lg:duration-150 lg:ease-out motion-reduce:transition-none" enter-from-class="lg:translate-y-3 lg:opacity-0">
          <div v-if="selectedMembers.length" role="region" :aria-label="$t('members.selectedAria')" class="flex flex-wrap items-center gap-x-4 gap-y-2 rounded-md border border-teal-line bg-teal-tint px-4 py-2.5 max-lg:sticky max-lg:top-14 max-lg:z-30 max-lg:mb-4 lg:pointer-events-auto lg:mx-auto lg:max-w-[960px] lg:border-rule lg:bg-paper lg:shadow-modal">
            <span class="text-base font-semibold text-teal">{{ selectionText }}</span>
            <span class="min-w-0 text-sm text-ink [overflow-wrap:anywhere]">{{ selectedNames }}</span>
            <TextButton @click="selectedIds = []">{{ $t('members.clearSelection') }}</TextButton>
            <div class="flex flex-wrap gap-2 md:ml-auto">
              <BaseButton variant="secondary" to="/communications">{{ $t('common.sendMessage') }}</BaseButton>
              <BaseButton variant="secondary" @click="exportSelected">{{ $t('members.exportSelected') }}</BaseButton>
              <BaseButton variant="secondary" :disabled="!inactiveTargets.length" @click="bulkAction = 'inactive'">{{ $t('members.markInactive') }}</BaseButton>
              <button v-if="authStore.isAdmin" type="button" :class="[DELETE_BUTTON, 'min-h-(--control-h) px-3 py-1.5 text-base']" @click="bulkAction = 'archive'">{{ $t('members.archive') }}</button>
            </div>
          </div>
        </Transition>
      </div>
      <label v-if="canSelect" class="mb-2 flex min-h-11 cursor-pointer items-center gap-3 text-base text-ink lg:hidden">
        <input type="checkbox" :class="CHECKBOX_PHONE" :checked="allSelected" :indeterminate="someSelected" @change="toggleAll($event.target.checked)">
        {{ $t('members.selectAllOnPage', { n: pagedMembers.length }) }}
      </label>

      <!-- Archived (ADMIN only): what is hidden, with the two things an administrator can do about it -->
      <template v-if="showingArchived">
        <div role="note" class="mb-4 rounded-md border border-rule bg-paper px-4 py-2.5 text-sm text-ink">
          {{ $t('members.archivedNote') }}
        </div>

        <table :class="TABLE">
          <caption class="sr-only">{{ $t('members.archivedCaption') }}</caption>
          <thead>
            <tr class="border-b border-rule">
              <th scope="col" :class="TH">{{ $t('members.colMember') }}</th>
              <th scope="col" :class="TH">{{ $t('members.colHousehold') }}</th>
              <th scope="col" :class="TH">{{ $t('members.stripHeader', { range: $t('strip.range', stripRange(today.slice(0, 7))) }) }}</th>
              <th scope="col" :class="TH">{{ $t('members.colLastPaid') }}</th>
              <th scope="col" :class="TH">{{ $t('members.colArchived') }}</th>
              <th scope="col" :class="TH"><span class="sr-only">{{ $t('members.colActions') }}</span></th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="member in pagedMembers" :key="member.id" class="border-b border-rule align-top">
              <td :class="[TD, 'max-w-0 w-[26%] py-3']">
                <div :class="[NAME, 'text-muted']">{{ member.name }}</div>
                <div v-if="member.email" class="text-sm text-muted [overflow-wrap:anywhere]">{{ member.email }}</div>
              </td>
              <td :class="[TD, 'py-3 text-muted']">
                <template v-if="member.householdName">{{ member.householdName }}</template>
                <template v-else>{{ $t('common.none') }}</template>
              </td>
              <td :class="[TD, 'py-3 whitespace-nowrap']">
                <YearStrip v-if="paidByMember" v-bind="stripProps(member)" />
                <span v-else class="text-muted"><span aria-hidden="true">&ndash;</span><span class="sr-only">{{ $t('members.monthsNotLoaded') }}</span></span>
              </td>
              <td :class="[TD, 'py-3 whitespace-nowrap']">{{ member.lastPaymentDate ? formatMemberDate(member.lastPaymentDate) : $t('members.never') }}</td>
              <td :class="[TD, 'py-3 whitespace-nowrap']">{{ archivedOn(member) }}</td>
              <td :class="[TD, 'py-3']">
                <div class="flex flex-wrap items-center justify-end gap-2">
                  <BaseButton variant="secondary" size="sm" :disabled="restoringId === member.id" @click="restoreMember(member)">
                    {{ $t('members.restore') }}<span class="sr-only"> {{ member.name }}</span>
                  </BaseButton>
                  <button type="button" :class="[DELETE_BUTTON, 'px-2.5 py-1 text-sm']" :disabled="hasPayments(member)" :aria-describedby="hasPayments(member) ? `keep-${member.id}` : undefined" @click="showPermanentModal(member)">
                    {{ $t('members.deleteForGood') }}<span class="sr-only"> {{ member.name }}</span>
                  </button>
                </div>
                <p v-if="hasPayments(member)" :id="`keep-${member.id}`" class="m-0 mt-1 text-right text-xs text-muted">{{ $t('members.hasPaymentsStay') }}</p>
              </td>
            </tr>
          </tbody>
        </table>

        <ul class="m-0 grid list-none grid-cols-1 gap-3 p-0 md:grid-cols-2 lg:hidden">
          <li v-for="member in pagedMembers" :key="member.id" class="flex flex-col gap-3 rounded-lg border border-rule bg-paper px-4 py-3.5">
            <div class="min-w-0">
              <div :class="[NAME, 'text-xl text-muted']">{{ member.name }}</div>
              <div v-if="member.email" class="text-sm text-muted [overflow-wrap:anywhere]">{{ member.email }}</div>
              <div v-if="member.householdName" class="flex items-center text-sm text-muted [overflow-wrap:anywhere]"><Icon name="home" :size="14" class="mr-1 shrink-0" /><span class="sr-only">{{ $t('members.householdLabel') }} </span>{{ member.householdName }}</div>
              <div class="mt-1 text-sm text-muted tabular-nums">
                {{ $t('members.lastPaid') }} {{ member.lastPaymentDate ? formatMemberDate(member.lastPaymentDate) : $t('members.neverLower') }} &middot; {{ archivedOn(member) }}
              </div>
            </div>
            <YearStrip v-if="paidByMember" size="large" v-bind="stripProps(member)" />
            <div class="flex flex-col gap-2">
              <div class="flex gap-2">
                <button type="button" :class="[PHONE_ACTION, 'flex-1 border border-field bg-paper text-ink hover:border-teal hover:bg-teal-tint disabled:pointer-events-none disabled:opacity-65']" :disabled="restoringId === member.id" @click="restoreMember(member)">
                  {{ $t('members.restore') }}<span class="sr-only"> {{ member.name }}</span>
                </button>
                <button type="button" :class="[DELETE_BUTTON, PHONE_ACTION, 'flex-1']" :disabled="hasPayments(member)" :aria-describedby="hasPayments(member) ? `keep-card-${member.id}` : undefined" @click="showPermanentModal(member)">
                  {{ $t('members.deleteForGood') }}<span class="sr-only"> {{ member.name }}</span>
                </button>
              </div>
              <p v-if="hasPayments(member)" :id="`keep-card-${member.id}`" class="m-0 text-sm text-muted">{{ $t('members.hasPaymentsStay') }}</p>
            </div>
          </li>
        </ul>
        <Pager v-bind="pagerProps" class="mt-4" @update:page="setPage" @update:page-size="setPageSize" />
      </template>

      <!-- lg and up: the table in a bordered card (grey header row, hairline between rows, footer line). Between lg and xl it keeps a
           minimum width and scrolls sideways inside the card rather than squeezing the columns -->
      <div v-else :class="['hidden overflow-x-auto rounded-md border border-rule bg-paper lg:block', selectedMembers.length ? 'lg:mb-28' : '']">
        <div class="lg:min-w-[56rem] xl:min-w-0">
          <table :class="TABLE">
            <caption class="sr-only">{{ $t('nav.members') }}</caption>
            <thead>
              <tr class="border-b border-rule">
                <th v-if="canSelect" scope="col" :class="[CARD_TH, 'w-8']">
                  <input type="checkbox" :class="CHECKBOX" :checked="allSelected" :indeterminate="someSelected" :aria-label="$t('members.selectAllOnPage', { n: pagedMembers.length })" @change="toggleAll($event.target.checked)">
                </th>
                <th v-for="column in columns" :key="column.labelKey || column.labelText" scope="col" :aria-sort="ariaSort(column.sortKey)" :class="[CARD_TH, column.class]">
                  <button v-if="column.sortKey" type="button" :class="SORT_BUTTON" @click="setSort(column.sortKey)">
                    {{ column.labelText || $t(column.labelKey) }}
                    <Icon :name="sortIcon(column.sortKey)" :size="12" :class="sort.key === column.sortKey ? 'text-ink' : 'text-muted'" />
                  </button>
                  <template v-else>{{ column.labelText || $t(column.labelKey) }}</template>
                </th>
                <th v-if="authStore.isStaff" scope="col" :class="[CARD_TH, 'w-14']"><span class="sr-only">{{ $t('members.colActions') }}</span></th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="member in pagedMembers" :key="member.id" :class="['h-(--row-h) border-b border-rule', isSelected(member) ? 'bg-teal-tint' : '']">
                <td v-if="canSelect" :class="CARD_TD">
                  <input type="checkbox" :class="CHECKBOX" :checked="isSelected(member)" :aria-label="$t('members.selectMember', { name: member.name })" @change="toggleSelected(member, $event.target.checked)">
                </td>
                <td :class="[CARD_TD, 'max-w-0 w-[26%]']">
                  <div :class="NAME"><router-link :to="`/members/${member.id}`">{{ member.name }}</router-link></div>
                  <div v-if="member.email" class="text-sm text-muted [overflow-wrap:anywhere]">{{ member.email }}</div>
                  <!-- the Phone column is hidden between lg and xl: its number sits under the email instead -->
                  <div v-if="member.phone" class="whitespace-nowrap text-sm text-muted xl:hidden">{{ member.phone }}</div>
                </td>
                <td :class="[CARD_TD, 'max-w-0 w-[14%] [overflow-wrap:anywhere]']">
                  <template v-if="member.householdName">{{ member.householdName }}</template>
                  <span v-else class="text-muted"><span aria-hidden="true">&ndash;</span><span class="sr-only">{{ $t('members.noHousehold') }}</span></span>
                </td>
                <td :class="[CARD_TD, 'whitespace-nowrap']">
                  <StatusBadge :tone="statusTone(member.status)">{{ $t(statusKey(member.status)) }}</StatusBadge>
                </td>
                <td :class="[CARD_TD, 'whitespace-nowrap']">
                  <YearStrip v-if="paidByMember" v-bind="stripProps(member)" />
                  <span v-else class="text-muted"><span aria-hidden="true">&ndash;</span><span class="sr-only">{{ $t('members.monthsNotLoaded') }}</span></span>
                </td>
                <td :class="[CARD_TD, 'whitespace-nowrap']">
                  <span :class="duesCell(member).class">{{ duesCell(member).text }}</span>
                </td>
                <td :class="[CARD_TD, 'whitespace-nowrap']">{{ member.lastPaymentDate ? formatMemberDate(member.lastPaymentDate) : $t('members.never') }}</td>
                <td :class="[CARD_TD, 'whitespace-nowrap text-muted max-xl:hidden']">
                  <template v-if="member.phone">{{ member.phone }}</template>
                  <span v-else><span aria-hidden="true">&ndash;</span><span class="sr-only">{{ $t('members.noPhone') }}</span></span>
                </td>
                <td v-if="authStore.isStaff" :class="[CARD_TD, 'text-right']">
                  <ActionMenu :label="$t('members.moreActions', { name: member.name })" :items="menuItems(member)" @select="key => onMenuSelect(key, member)" />
                </td>
              </tr>
            </tbody>
          </table>
          <Pager v-bind="pagerProps" class="px-4 py-3" @update:page="setPage" @update:page-size="setPageSize" />
          <div :class="['flex flex-wrap justify-between gap-x-4 gap-y-2 px-4 text-sm text-muted', pagerShown ? 'pb-3' : 'py-3']">
            <span v-if="!pagerShown">{{ countText }}</span>
            <span class="ml-auto">{{ $t('members.asOf', { date: asOfText }) }}</span>
          </div>
        </div>
      </div>

      <!-- Below lg: one card per member, two across from md -->
      <ul v-if="!showingArchived" class="m-0 grid list-none grid-cols-1 gap-3 p-0 md:grid-cols-2 lg:hidden">
        <li v-for="member in pagedMembers" :key="member.id" class="flex flex-col gap-3 rounded-lg border border-rule bg-paper px-4 py-3.5">
          <div class="flex items-start justify-between gap-2">
            <label v-if="canSelect" class="-ml-2 -mt-1.5 flex h-11 w-11 shrink-0 cursor-pointer items-center justify-center">
              <input type="checkbox" :class="CHECKBOX_PHONE" :checked="isSelected(member)" :aria-label="$t('members.selectMember', { name: member.name })" @change="toggleSelected(member, $event.target.checked)">
            </label>
            <div class="min-w-0 flex-1">
              <div :class="[NAME, 'text-xl']"><router-link :to="`/members/${member.id}`" class="inline-block py-2 -my-2">{{ member.name }}</router-link></div>
              <div v-if="member.email" class="text-sm text-muted [overflow-wrap:anywhere]">{{ member.email }}</div>
              <div v-if="member.householdName" class="flex items-center text-sm text-muted [overflow-wrap:anywhere]"><Icon name="home" :size="14" class="mr-1 shrink-0" /><span class="sr-only">{{ $t('members.householdLabel') }} </span>{{ member.householdName }}</div>
              <div class="mt-1 flex flex-wrap items-center gap-x-4">
                <StatusLabel :tone="statusTone(member.status)">{{ $t(statusKey(member.status)) }}</StatusLabel>
                <span v-if="countsForDues(member)" :class="[duesClass(member), 'text-lg']">{{ duesText(member) }}</span>
              </div>
              <div class="mt-1 text-sm text-muted tabular-nums">
                <template v-if="member.phone">{{ member.phone }} &middot; </template>{{ $t('members.joined') }} {{ formatMemberDate(member.joinDate) }} &middot; {{ $t('members.lastPaid') }} {{ member.lastPaymentDate ? formatMemberDate(member.lastPaymentDate) : $t('members.neverLower') }}
              </div>
            </div>
            <ActionMenu v-if="authStore.isStaff" :label="$t('members.moreActions', { name: member.name })" :items="menuItems(member)" @select="key => onMenuSelect(key, member)" />
          </div>
          <YearStrip v-if="paidByMember" size="large" v-bind="stripProps(member)" />
          <div v-if="member.phone || (authStore.isStaff && countsForDues(member))" class="flex gap-2">
            <a v-if="member.phone" :href="`tel:${member.phone.replace(/[^+\d]/g, '')}`" :class="[PHONE_ACTION, 'flex-1 border border-field bg-paper text-ink hover:border-teal hover:bg-teal-tint']">
              <Icon name="phone" :size="18" />{{ $t('members.call') }}<span class="sr-only"> {{ member.name }}</span>
            </a>
            <router-link v-if="authStore.isStaff && countsForDues(member)" :to="{ path: '/payments', query: { memberId: member.id } }" :class="[PHONE_ACTION, 'flex-[1.4] border border-teal bg-teal text-paper hover:bg-teal-hover']">
              {{ $t('common.recordPayment') }}<span class="sr-only"> {{ member.name }}</span>
            </router-link>
          </div>
        </li>
      </ul>
      <Pager v-if="!showingArchived" v-bind="pagerProps" class="mt-4 lg:hidden" @update:page="setPage" @update:page-size="setPageSize" />
    </template>

    <!-- Add and edit -->
    <MemberFormDialog v-model="formOpen" :member="editingMember" :focus-status="focusStatus" @saved="reloadLists" />

    <!-- Delete for good (ADMIN only, archived members) -->
    <ConfirmDialog
      v-model="permanentOpen"
      :title="$t('members.deleteTitle', { name: selectedMember?.name || $t('nav.member') })"
      :message="$t('members.deleteMessage', { name: selectedMember?.name || $t('nav.member') })"
      :confirm-label="$t('members.deleteForGood')"
      danger
      :busy="deletingPermanently"
      @confirm="deletePermanently"
    />

    <!-- Bulk: Mark inactive and Archive for the selected members -->
    <ConfirmDialog
      :model-value="bulkAction === 'inactive'"
      :title="$t('members.markInactiveTitle', inactiveTargets.length)"
      :message="inactiveMessage"
      :confirm-label="$t('members.markInactive')"
      :busy="bulkBusy"
      @update:model-value="open => { if (!open) bulkAction = null }"
      @confirm="markSelectedInactive"
    />
    <ConfirmDialog
      :model-value="bulkAction === 'archive'"
      :title="$t('members.archiveTitle', selectedMembers.length)"
      :message="$t('members.archiveMessage', selectedMembers.length)"
      :confirm-label="$t('members.archiveConfirm', selectedMembers.length)"
      danger
      :busy="bulkBusy"
      @update:model-value="open => { if (!open) bulkAction = null }"
      @confirm="archiveSelected"
    />

    <!-- Archive (ADMIN only) -->
    <MemberArchiveDialog v-model="deleteOpen" :member="selectedMember" @archived="reloadLists" />
    </div>
  </div>
</template>

<script>
import api from '@/services/api'
import { useAppStore } from '../stores/appStore'
import { useAuthStore } from '../stores/authStore'
import { downloadBlob, formatDate, localISODate } from '@/utils'
import { monthsBehind } from '@/utils/dues'
import { paidMonthsFromMap, stripRange } from '@/utils/yearStrip'
import { STATUS_SEGMENTS, filterMembers, sortMembers, statusCounts, exportIds } from '@/utils/memberFilters'
import { membersCsv } from '@/utils/memberCsv'
import { clampPage, pageSlice, PAGE_SIZES } from '@/utils/paging'
import { queryPaging } from '@/utils/queryPaging'
import { buildMemberRequest } from '@/utils/memberPayload'
import { countsForDues, statusKey, statusTone } from '@/utils/memberStatus'
import ActionMenu from '@/components/ActionMenu.vue'
import AlertBanner from '@/components/AlertBanner.vue'
import BaseButton from '@/components/BaseButton.vue'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import EmptyNote from '@/components/EmptyNote.vue'
import Icon from '@/components/Icon.vue'
import MemberArchiveDialog from '@/components/MemberArchiveDialog.vue'
import MemberFormDialog from '@/components/MemberFormDialog.vue'
import PageHead from '@/components/PageHead.vue'
import Pager from '@/components/Pager.vue'
import StatusBadge from '@/components/StatusBadge.vue'
import StatusLabel from '@/components/StatusLabel.vue'
import YearStrip from '@/components/YearStrip.vue'
import TextButton from '@/components/TextButton.vue'
import { CONTROL, LABEL, NAME, SORT_BUTTON, TABLE_FROM_LG as TABLE, TABLE_TH as TH, TABLE_TD as TD, TABLE_CARD_TH as CARD_TH, TABLE_CARD_TD as CARD_TD } from '@/ui/classes'

// A 44px tap target for the card's two actions
const PHONE_ACTION = 'flex min-h-11 items-center justify-center gap-2 rounded-md px-4 text-lg font-medium no-underline'
// "Delete for good": an outline in clay (the dialog holds the solid danger button)
const DELETE_BUTTON = 'inline-flex cursor-pointer items-center justify-center rounded-sm border border-clay bg-paper font-medium leading-normal text-clay hover:bg-clay-tint disabled:pointer-events-none disabled:border-rule disabled:text-muted disabled:opacity-65'
// One button of the Status segmented control; segmentShape rounds the two ends and joins the borders
const SEGMENT = 'relative -ml-px first:ml-0 inline-flex min-h-11 shrink-0 cursor-pointer items-center gap-1 border px-3 text-base font-medium whitespace-nowrap lg:min-h-(--control-h) lg:text-sm'
// The Sort by control: the first click on a header starts ascending, but here "Most behind" and
// "Joined" mean the most behind and the newest first
const SORT_OPTIONS = [
  { key: 'name', labelKey: 'members.sortName', direction: 'asc' },
  { key: 'consecutiveMonthsMissed', labelKey: 'members.sortMostBehind', direction: 'desc' },
  { key: 'joinDate', labelKey: 'members.sortJoined', direction: 'desc' }
]
// A row checkbox (16px; its cell is the tap area on a desktop) and the 44px-box one on a phone card
const CHECKBOX = 'h-4 w-4 cursor-pointer accent-teal'
const CHECKBOX_PHONE = 'h-5 w-5 cursor-pointer accent-teal'
const EMPTY_FILTERS = { search: '', status: 'ALL', paymentStatus: 'ALL', joinedFrom: '', joinedTo: '' }
// /members?dues= (the Overview's "See all" link) and the Dues filter's own values
const DUES_FILTER = { behind: 'OVERDUE', paid: 'CURRENT', all: 'ALL' }
const DUES_QUERY = { OVERDUE: 'behind', CURRENT: 'paid' }
// the opening filters: the search (/households links to a member's name) and the dues (the Overview's "See all") a link carries
const filtersFromQuery = (query) => ({
  ...EMPTY_FILTERS,
  search: typeof query?.search === 'string' ? query.search : '',
  paymentStatus: DUES_FILTER[query?.dues] || 'ALL'
})

export default {
  name: 'MembersView',
  mixins: [queryPaging()],
  components: { ActionMenu, AlertBanner, BaseButton, ConfirmDialog, EmptyNote, Icon, MemberArchiveDialog, MemberFormDialog, PageHead, Pager, StatusBadge, StatusLabel, TextButton, YearStrip },
  setup() {
    return {
      appStore: useAppStore(),
      authStore: useAuthStore()
    }
  },
  data() {
    return {
      members: [],
      paidByMember: null,
      archivedMembers: [],
      archivedLoaded: false,
      loaded: false,
      loadError: false,
      filters: filtersFromQuery(this.$route?.query),
      filtersOpen: false,
      moreOpen: false,
      sort: { key: 'name', direction: 'asc' },
      selectedIds: [],
      bulkAction: null,
      bulkBusy: false,
      formOpen: false,
      focusStatus: false,
      editingMember: null,
      selectedMember: null,
      deleteOpen: false,
      permanentOpen: false,
      deletingPermanently: false,
      restoringId: null,
      today: localISODate(),
      PHONE_ACTION,
      DELETE_BUTTON,
      stripRange,
      TABLE,
      LABEL,
      CONTROL,
      TH,
      TD,
      CARD_TH,
      CARD_TD,
      SORT_BUTTON,
      NAME,
      SEGMENT,
      CHECKBOX,
      CHECKBOX_PHONE,
      SORT_OPTIONS,
      countsForDues,
      statusKey,
      statusTone
    }
  },
  computed: {
    showingArchived() {
      return this.filters.status === 'ARCHIVED' && this.authStore.isAdmin
    },
    // the list on screen: the archived list (ADMIN, loaded on demand) or the normal one
    source() {
      return this.showingArchived ? this.archivedMembers : this.members
    },
    // the segments with their counts; the archived list loads on demand, so its count shows once it has
    statusSegments() {
      const counts = statusCounts(this.members)
      const segments = STATUS_SEGMENTS.map(segment => ({ ...segment, count: counts[segment.value] }))
      if (this.authStore.isAdmin) segments.push({ value: 'ARCHIVED', labelKey: 'status.archived', count: this.archivedLoaded ? this.archivedMembers.length : null })
      return segments
    },
    filteredMembers() {
      const filtered = filterMembers(this.source, this.filters)
      return this.sort.key ? sortMembers(filtered, this.sort.key, this.sort.direction) : filtered
    },
    // a page beyond the last shows as the last
    currentPage() {
      return clampPage(this.page, this.filteredMembers.length, this.pageSize)
    },
    // the rows on screen: one page of the sorted, filtered list
    pagedMembers() {
      return pageSlice(this.filteredMembers, this.currentPage, this.pageSize)
    },
    pagerProps() {
      return { page: this.currentPage, pageSize: this.pageSize, total: this.filteredMembers.length }
    },
    pagerShown() {
      return this.filteredMembers.length > Math.min(...PAGE_SIZES)
    },
    // changes to what the list holds or how it is ordered: back to page 1
    listKey() {
      return `${JSON.stringify(this.filters)}|${this.sort.key}|${this.sort.direction}`
    },
    // Selecting is for STAFF+ and for the normal list. The selection spans pages (what a filter hides is not selected);
    // the header checkbox and "Select all" act on the page on screen only
    canSelect() {
      return this.authStore.isStaff && !this.showingArchived
    },
    selectedMembers() {
      return this.canSelect ? this.filteredMembers.filter(member => this.selectedIds.includes(member.id)) : []
    },
    selectedOnPage() {
      return this.canSelect ? this.pagedMembers.filter(member => this.selectedIds.includes(member.id)) : []
    },
    selectedOffPage() {
      return this.selectedMembers.length - this.selectedOnPage.length
    },
    allSelected() {
      return this.canSelect && this.pagedMembers.length > 0 && this.selectedOnPage.length === this.pagedMembers.length
    },
    someSelected() {
      return this.selectedOnPage.length > 0 && !this.allSelected
    },
    // Mark inactive only changes a Member: an inactive, transferred or deceased one is left as it is
    inactiveTargets() {
      return this.selectedMembers.filter(countsForDues)
    },
    inactiveMessage() {
      const skipped = this.selectedMembers.length - this.inactiveTargets.length
      const base = this.$t('members.markInactiveMessage')
      return skipped ? `${base} ${this.$t('members.markInactiveSkipped', skipped)}` : base
    },
    // the first three names, then how many more
    selectedNames() {
      const names = this.selectedMembers.map(member => member.name)
      return names.length > 3
        ? this.$t('members.andMore', { names: names.slice(0, 3).join(this.$t('common.listSeparator')), n: names.length - 3 })
        : names.join(this.$t('common.listSeparator'))
    },
    // "3 selected" or, when some are on other pages, "5 selected, 2 not on this page"
    selectionText() {
      const n = this.selectedMembers.length
      if (!this.selectedOffPage) return this.$t('members.selected', { n })
      return `${this.$t('members.selected', { n })}, ${this.$t('members.selectedOffPage', { off: this.selectedOffPage })}`
    },
    selectionAnnouncement() {
      return this.selectedMembers.length ? this.selectionText : ''
    },
    countText() {
      const total = this.source.length
      const noun = this.showingArchived
        ? this.$t('members.countArchivedMember', total)
        : this.$t('members.countMember', total)
      return this.filteredMembers.length !== total
        ? this.$t('members.countOf', { shown: this.filteredMembers.length, total: noun })
        : noun
    },
    hasActiveFilters() {
      const f = this.filters
      return !!f.search.trim() || f.status !== 'ALL' || f.paymentStatus !== 'ALL' || !!f.joinedFrom || !!f.joinedTo
    },
    // how many of the two dates are set: what the desktop "More filters" button holds
    dateFilterCount() {
      return (this.filters.joinedFrom ? 1 : 0) + (this.filters.joinedTo ? 1 : 0)
    },
    // what the phone "Filters" button holds that is set: Dues and the two dates (status and search stay in view)
    panelFilterCount() {
      return (this.filters.paymentStatus !== 'ALL' ? 1 : 0) + this.dateFilterCount
    },
    columns() {
      return [
        { labelKey: 'members.colMember', sortKey: 'name' },
        { labelKey: 'members.colHousehold' },
        { labelKey: 'members.colStatus' },
        { labelText: this.$t('members.stripHeader', { range: this.$t('strip.range', stripRange(this.today.slice(0, 7))) }) },
        { labelKey: 'members.colDues', sortKey: 'consecutiveMonthsMissed' },
        { labelKey: 'members.colLastPaid' },
        { labelKey: 'members.colPhone', class: 'max-xl:hidden' }
      ]
    },
    // the table card's footer: today as a long date
    asOfText() {
      return formatDate(this.today, 'EEEE, d MMMM yyyy')
    }
  },
  watch: {
    'filters.status'(status) {
      if (status === 'ARCHIVED' && this.authStore.isAdmin) this.loadArchived()
    },
    listKey() {
      this.resetPage()
    },
    // the list changed length (loaded, a member archived): a page beyond the last becomes the last
    'filteredMembers.length'(length) {
      if (this.loaded) this.settlePage(length)
    },
    // the Overview's "See all" links /members?dues=behind while this screen may already be open
    '$route.query.dues'(dues) {
      const paymentStatus = DUES_FILTER[dues]
      if (paymentStatus && paymentStatus !== this.filters.paymentStatus) this.filters.paymentStatus = paymentStatus
    },
    // the top bar search pushes /members?search= while this screen is already open
    '$route.query.search'(search) {
      if (typeof search === 'string') this.filters.search = search
    }
  },
  async created() {
    await Promise.all([this.loadMembers(), this.loadPayments()])
  },
  methods: {
    // the Dues filter rides in the URL beside page and size (omitted for All dues)
    pagingExtraQuery() {
      return { dues: DUES_QUERY[this.filters.paymentStatus] }
    },
    async loadMembers() {
      try {
        const data = await api.getMembers()
        // Ensure members is always an array
        this.members = Array.isArray(data) ? data : []
        this.loadError = false
      } catch (error) {
        console.error('Error loading members:', error)
        this.members = []
        this.loadError = true
      } finally {
        this.loaded = true
      }
    },
    // The paid months behind the year strip: GET /payments/paid-months, grouped by member. A failure only hides the strips.
    async loadPayments() {
      try {
        this.paidByMember = paidMonthsFromMap(await api.getPaidMonths(12))
      } catch (error) {
        console.error('Error loading payments for the year strip:', error)
        this.paidByMember = null
      }
    },
    stripProps(member) {
      return {
        joinDate: member.joinDate || '',
        paidMonths: this.paidByMember.get(member.id) || new Set(),
        currentMonth: this.today.slice(0, 7),
        monthsMissed: member.consecutiveMonthsMissed || 0,
        countsForDues: countsForDues(member),
        muted: this.showingArchived,
        label: this.$t('strip.duesFor', { name: member.name })
      }
    },
    async loadArchived() {
      try {
        const data = await api.getMembers({ archived: true })
        this.archivedMembers = Array.isArray(data) ? data : []
      } catch (error) {
        console.error('Error loading archived members:', error)
        this.archivedMembers = []
        this.notifyFailure(this.$t('members.couldNotLoadArchived'), error)
      } finally {
        this.archivedLoaded = true
      }
    },
    // after a change: the normal list, and the archived one when it is on screen
    async reloadLists() {
      await Promise.all([this.loadMembers(), this.loadPayments()])
      if (this.showingArchived) await this.loadArchived()
    },
    setSort(key) {
      if (this.sort.key === key) {
        this.sort.direction = this.sort.direction === 'asc' ? 'desc' : 'asc'
      } else {
        this.sort = { key, direction: 'asc' }
      }
    },
    isSelected(member) {
      return this.selectedIds.includes(member.id)
    },
    toggleSelected(member, checked) {
      this.selectedIds = checked
        ? [...this.selectedIds, member.id]
        : this.selectedIds.filter(id => id !== member.id)
    },
    // the header checkbox: the rows on this page only; what was selected on other pages stays as it is
    toggleAll(checked) {
      const shown = this.pagedMembers.map(member => member.id)
      this.selectedIds = checked
        ? [...new Set([...this.selectedIds, ...shown])]
        : this.selectedIds.filter(id => !shown.includes(id))
    },
    // One call per member, one after the other. Failures never stop the rest: the ones that worked are
    // reported with the ones that did not, and only the failed members stay selected.
    async runBulk(targets, call, successKey) {
      this.bulkBusy = true
      const failed = []
      let firstError = null
      for (const member of targets) {
        try {
          await call(member)
        } catch (error) {
          console.error(`Error: ${successKey} ${member.name}:`, error)
          failed.push(member)
          firstError = firstError || error
        }
      }
      const done = targets.length - failed.length
      this.selectedIds = failed.map(member => member.id)
      this.bulkAction = null
      try {
        await this.reloadLists()
      } finally {
        this.bulkBusy = false
      }
      if (!failed.length) {
        this.notify('success', this.$t(successKey), this.$t('members.countMember', done))
      } else if (firstError.response?.status !== 403) {
        this.notify(
          'error',
          this.$t('members.bulkFailedTitle', { done, total: targets.length }),
          this.$t('members.bulkFailedMessage', {
            names: failed.map(member => member.name).join(this.$t('common.listSeparator')),
            error: firstError.message || this.$t('common.requestFailed')
          })
        )
      }
    },
    markSelectedInactive() {
      return this.runBulk(this.inactiveTargets, member => api.updateMember(member.id, buildMemberRequest({ ...member, status: 'INACTIVE' })), 'members.markedInactive')
    },
    archiveSelected() {
      return this.runBulk(this.selectedMembers, member => api.deleteMember(member.id), 'members.archivedTitle')
    },
    exportSelected() {
      downloadBlob(membersCsv(this.selectedMembers), `members_selected_${new Date().toISOString().split('T')[0]}.csv`)
    },
    setSortOption(key) {
      this.sort = { key, direction: SORT_OPTIONS.find(option => option.key === key).direction }
    },
    ariaSort(key) {
      if (!key) return undefined
      if (this.sort.key !== key) return 'none'
      return this.sort.direction === 'asc' ? 'ascending' : 'descending'
    },
    sortIcon(key) {
      if (this.sort.key !== key) return 'chevrons-up-down'
      return this.sort.direction === 'asc' ? 'caret-up' : 'caret-down'
    },
    segmentShape(value) {
      const last = this.statusSegments[this.statusSegments.length - 1].value
      return [value === 'ALL' ? 'rounded-l-sm' : '', value === last ? 'rounded-r-sm' : '']
    },
    clearFilters() {
      this.filters = { ...EMPTY_FILTERS }
    },
    closeMore() {
      if (!this.moreOpen) return
      this.moreOpen = false
      this.$refs.moreButton?.focus()
    },
    formatMemberDate(date) {
      return date ? formatDate(date, 'MMM d, yyyy') : ''
    },
    // Dues are tracked for members with status MEMBER only; for any other status the stored figure is stale
    duesText(member) {
      return member.consecutiveMonthsMissed > 0 ? monthsBehind(member.consecutiveMonthsMissed, this.$t) : this.$t('dues.badgePaid')
    },
    duesClass(member) {
      if (!countsForDues(member)) return 'text-muted'
      return ['font-medium', member.consecutiveMonthsMissed > 0 ? 'text-ochre-text' : 'text-fern-text']
    },
    // The Dues cell of the table: months behind in clay, "Due now" in ochre when this month is still unpaid, "Paid up" in fern,
    // "No dues" for anyone who is not a Member (the strip's rule: payments must have loaded to tell "due now")
    duesCell(member) {
      if (!countsForDues(member)) return { text: this.$t('members.noDues'), class: 'text-muted' }
      const behind = member.consecutiveMonthsMissed
      if (behind > 0) return { text: this.$t('members.monthsCount', behind), class: 'font-semibold text-clay' }
      const month = this.today.slice(0, 7)
      const joined = !member.joinDate || member.joinDate.slice(0, 7) <= month
      if (joined && this.paidByMember && !this.paidByMember.get(member.id)?.has(month)) return { text: this.$t('members.dueNow'), class: 'font-semibold text-ochre-text' }
      return { text: this.$t('dues.badgePaid'), class: 'font-semibold text-fern-text' }
    },
    menuItems(member) {
      const items = [{ key: 'edit', label: this.$t('members.edit') }]
      if (member.status === 'MEMBER') items.push({ key: 'toggle', label: this.$t('members.markInactive') })
      else if (member.status === 'INACTIVE') items.push({ key: 'toggle', label: this.$t('members.markActive') })
      items.push({ key: 'status', label: this.$t('members.changeStatus') })
      if (this.authStore.isAdmin) items.push({ key: 'delete', label: this.$t('members.archive'), danger: true })
      return items
    },
    onMenuSelect(key, member) {
      if (key === 'edit') this.showEditModal(member)
      else if (key === 'status') this.showEditModal(member, true)
      else if (key === 'toggle') this.toggleStatus(member)
      else if (key === 'delete') this.showDeleteModal(member)
    },
    showAddModal() {
      this.editingMember = null
      this.focusStatus = false
      this.today = localISODate()
      this.formOpen = true
    },
    showEditModal(member, focusStatus = false) {
      this.editingMember = member
      this.focusStatus = focusStatus
      this.today = localISODate()
      this.formOpen = true
    },
    showDeleteModal(member) {
      this.selectedMember = member
      this.deleteOpen = true
    },
    async toggleStatus(member) {
      const reactivating = member.status !== 'MEMBER'
      try {
        await api.updateMember(member.id, buildMemberRequest({ ...member, status: reactivating ? 'MEMBER' : 'INACTIVE' }))
        await this.reloadLists()
        this.notify('success', reactivating ? this.$t('members.memberReactivated') : this.$t('members.memberDeactivated'), member.name)
      } catch (error) {
        console.error('Error toggling member status:', error)
        this.notifyFailure(reactivating ? this.$t('members.couldNotReactivate') : this.$t('members.couldNotDeactivate'), error)
      }
    },
    // the archived list is not the normal list: the payments loaded for the strips say whether a member has history
    hasPayments(member) {
      return !!this.paidByMember?.get(member.id)?.size
    },
    // archivedAt is a date-time: the day is all the screen shows (the API does not say who archived)
    archivedOn(member) {
      return member.archivedAt ? this.formatMemberDate(member.archivedAt.slice(0, 10)) : ''
    },
    showPermanentModal(member) {
      this.selectedMember = member
      this.permanentOpen = true
    },
    async deletePermanently() {
      const { id, name } = this.selectedMember
      this.deletingPermanently = true
      try {
        await api.deleteMemberPermanently(id)
        await this.reloadLists()
        this.permanentOpen = false
        this.notify('success', this.$t('members.deletedForGood'), name)
      } catch (error) {
        console.error('Error deleting member for good:', error)
        this.permanentOpen = false
        if (error.response?.status === 409) {
          this.notify('error', this.$t('members.couldNotDelete'), this.$t('members.hasHistory', { name }))
        } else {
          this.notifyFailure(this.$t('members.couldNotDeleteMember'), error)
        }
      } finally {
        this.deletingPermanently = false
      }
    },
    async restoreMember(member) {
      this.restoringId = member.id
      try {
        await api.updateMember(member.id, buildMemberRequest({ ...member, status: 'MEMBER' }))
        await this.reloadLists()
        this.notify('success', this.$t('members.restored'), member.name)
      } catch (error) {
        console.error('Error restoring member:', error)
        this.notifyFailure(this.$t('members.couldNotRestore'), error)
      } finally {
        this.restoringId = null
      }
    },
    notify(type, title, message) {
      this.appStore.addNotification({ type, title, message, isToast: true })
    },
    // The shared API handler already shows an "Access Denied" toast for 403
    notifyFailure(title, error) {
      if (error.response?.status === 403) return
      this.notify('error', title, error.message || this.$t('common.requestFailed'))
    },
    async exportMembers() {
      if (this.filteredMembers.length === 0) {
        this.appStore.addNotification({
          type: 'warning',
          title: this.$t('members.nothingToExport'),
          message: this.$t('members.nothingToExportMessage'),
          isToast: true
        })
        return
      }
      try {
        // the archived list is not the full list the endpoint exports, so it always goes by ids
        const ids = this.showingArchived ? this.filteredMembers.map(member => member.id) : exportIds(this.filteredMembers, this.members)
        const response = await api.exportMembers(ids)
        // api.request() already returns response.data (the blob)
        const prefix = this.hasActiveFilters ? 'members_filtered' : 'members'
        downloadBlob(response, `${prefix}_${new Date().toISOString().split('T')[0]}.csv`)
      } catch (error) {
        console.error('Error exporting members:', error)
        this.appStore.addNotification({
          type: 'error',
          title: this.$t('common.exportFailed'),
          message: error.message || this.$t('common.couldNotExportCsv'),
          isToast: true
        })
      }
    }
  }
}
</script>
