package com.example.hotel.common;

import java.util.List;

public record PageDto<T>(List<T> content, int page, int size, long totalElements, int totalPages) {
  public static void validate(int page, int size) {
    if (page < 0 || size < 1 || size > 100)
      throw ApiException.badRequest("page >= 0; size between 1 and 100");
  }
}
