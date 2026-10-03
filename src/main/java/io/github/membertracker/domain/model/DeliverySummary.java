package io.github.membertracker.domain.model;

/** How many deliveries of one communication are in each status. Derived, never stored. */
public record DeliverySummary(int sent, int failed, int pending, int delivered) {

    public static final DeliverySummary EMPTY = new DeliverySummary(0, 0, 0, 0);

    public int total() {
        return sent + failed + pending + delivered;
    }
}
