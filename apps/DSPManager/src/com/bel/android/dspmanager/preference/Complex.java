package com.bel.android.dspmanager.preference;

/**
 * Java support for complex numbers.
 * 
 * @author alankila
 */
class Complex {
	final float re, im;
	
	protected Complex(float re, float im) {
		this.re = re;
		this.im = im;
	}
	
	/**
	 * Length of complex number
	 * 
	 * @return length
	 */
	protected float rho() {
		return (float) Math.sqrt(re * re + im * im);
	}
	
	/**
	 * Argument of complex number
	 * 
	 * @return angle in radians
	 */
	protected float theta() {
		return (float) Math.atan2(im, re);
	}
	
	/**
	 * Complex conjugate
	 * 
	 * @return conjugate
	 */
	protected Complex con() {
		return new Complex(re, -im);
	}

	/**
	 * Complex addition
	 * 
	 * @param other
	 * @return sum
	 */
	protected Complex add(Complex other) {
		return new Complex(re + other.re, im + other.im);
	}
	
	/**
	 * Complex multipply
	 * 
	 * @param other
	 * @return multiplication result
	 */
	protected Complex mul(Complex other) {
		return new Complex(re * other.re - im * other.im, re * other.im + im * other.re);
	}
	
	/**
	 * Complex multiply with real value
	 * 
	 * @param a
	 * @return multiplication result
	 */
	protected Complex mul(float a) {
		return new Complex(re * a, im * a);
	}
	
	/**
	 * Complex division
	 * 
	 * @param other
	 * @return division result
	 */
	protected Complex div(Complex other) {
	    float lengthSquared = other.re * other.re + other.im * other.im;
	    return mul(other.con()).div(lengthSquared);
	}
	
	/**
	 * Complex division with real value
	 * 
	 * @param a
	 * @return division result
	 */
	protected Complex div(float a) {
		return new Complex(re/a, im/a);
	}
}