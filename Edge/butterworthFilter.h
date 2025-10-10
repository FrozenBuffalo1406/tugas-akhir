#ifndef BUTTERWORTH_FILTER_H
#define BUTTERWORTH_FILTER_H

class ButterworthFilter {
public:
  ~ButterworthFilter();
  ButterworthFilter(const float* b_coeffs, const float* a_coeffs, int order);
  float update(float newSample);
  void reset();
private:
  const int _order;
  float* _b;
  float* _a;
  float* _w;
};

#endif