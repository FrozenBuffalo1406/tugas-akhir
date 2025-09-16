#include "butterworth.h"
#include <string.h>

FilterButterworth::FilterButterworth(const double* b_coeffs, const double* a_coeffs, int order) :
    _order(order) {
    _b = new double[_order + 1];
    _a = new double[_order + 1];
    _x = new double[_order + 1];
    _y = new double[_order + 1];

    memcpy(_b, b_coeffs, (_order + 1) * sizeof(double));
    memcpy(_a, a_coeffs, (_order + 1) * sizeof(double));

    reset();
}

void FilterButterworth::reset() {
    memset(_x, 0, (_order + 1) * sizeof(double));
    memset(_y, 0, (_order + 1) * sizeof(double));
}

float FilterButterworth::filter(float input) {
    for (int i = _order; i > 0; --i) {
        _x[i] = _x[i - 1];
    }
    _x[0] = input;

    double output = 0.0;

    for (int i = 0; i <= _order; ++i) {
        output += _b[i] * _x[i];
    }

    for (int i = 1; i <= _order; ++i) {
        output -= _a[i] * _y[i];
    }
    output /= _a[0];

    for (int i = _order; i > 0; --i) {
        _y[i] = _y[i - 1];
    }
    _y[0] = output;

    return (float)output;
}