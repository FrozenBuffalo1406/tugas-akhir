#include "ButterworthFilter.h"
#include <string.h>

ButterworthFilter::ButterworthFilter(const float* b_coeffs, const float* a_coeffs, int order) : _order(order) {
  _b = new float[_order + 1];
  _a = new float[_order + 1];
  _w = new float[_order];
  memcpy(_b, b_coeffs, (_order + 1) * sizeof(float));
  memcpy(_a, a_coeffs, (_order + 1) * sizeof(float));
  reset();
}

ButterworthFilter::~ButterworthFilter() {
  delete[] _b;
  delete[] _a;
  delete[] _w;
}

void ButterworthFilter::reset() {
  memset(_w, 0, _order * sizeof(float));
}

float ButterworthFilter::update(float newSample) {
  float output = _b[0] * newSample + _w[0];
  for (int i = 0; i < _order - 1; i++) {
    _w[i] = _b[i + 1] * newSample - _a[i + 1] * output + _w[i + 1];
  }
  _w[_order - 1] = _b[_order] * newSample - _a[_order] * output;
  return output;
}
