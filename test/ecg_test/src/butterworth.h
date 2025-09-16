#ifndef BUTTERWORTH_H
#define BUTTERWORTH_H

#include <math.h>
#include <string.h>

class FilterButterworth {
public:
    FilterButterworth(const double* b_coeffs, const double* a_coeffs, int order);
    float filter(float input);
    void reset();

private:
    int _order;
    double* _b;
    double* _a;
    double* _x;
    double* _y;
};

#endif
