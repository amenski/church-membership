package io.github.membertracker.domain.model;

import java.util.List;

/** One page of a larger list plus the numbers a pager needs. {@code page} is zero-based. */
public record PageResult<T>(List<T> content, int page, int size, long totalElements, int totalPages) {
}
