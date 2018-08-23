/*
 * Copyright (c) IBM Corporation 2018. All Rights Reserved.
 * Project name: pross
 * This project is licensed under the MIT License, see LICENSE.
 */

package com.ibm.pross.common.util.shamir;

import static org.junit.Assert.fail;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.Assert;
import org.junit.Test;

import com.ibm.pross.common.CommonConfiguration;
import com.ibm.pross.common.util.RandomNumberGenerator;
import com.ibm.pross.common.util.crypto.ecc.EcCurve;
import com.ibm.pross.common.util.crypto.ecc.EcPoint;

public class ShamirTest {

	// Static fields
	final public static EcCurve curve = CommonConfiguration.CURVE;
	final public static BigInteger r = curve.getR();
	final public static EcPoint G = curve.getG();

	@Test
	public void testGenerateCoefficientsInt() {
		fail("Not yet implemented");
	}

	@Test
	public void testGenerateCoefficientsIntInt() {

		int n = 9;
		int threshold = 5;
		int repairIndex = 2;

		// Create coefficients
		final BigInteger[] coefficients = Shamir.generateCoefficients(threshold, repairIndex);

		// Create shares
		final Set<ShamirShare> shares = new HashSet<>();
		for (int i = 1; i <= n; i++) {
			final BigInteger x = BigInteger.valueOf(i);
			final ShamirShare share = Polynomials.evaluatePolynomial(coefficients, x, r);
			shares.add(share);
		}

		// Interpolate at different points, ensure non-zero at all positions
		// except repair index
		for (int i = 0; i <= n; i++) {
			if (i == repairIndex) {
				final BigInteger result = Polynomials.interpolateComplete(shares, threshold, i);
				Assert.assertEquals(BigInteger.ZERO, result);
			} else {
				final BigInteger result = Polynomials.interpolateComplete(shares, threshold, i);
				Assert.assertNotEquals(BigInteger.ZERO, result);
			}
		}

		// Generate feldman values
		final EcPoint[] feldmanValues = Shamir.generateFeldmanValues(coefficients);

		// Verify feldman co-efficients are consistent with f(repair_index) == 0
		final ShamirShare zeroIntercept = new ShamirShare(BigInteger.valueOf(repairIndex), BigInteger.ZERO);
		Shamir.verifyShamirShareConsistency(zeroIntercept, feldmanValues);
	}

	@Test
	public void testGenerateCoefficientsMaskReconstruction() {

		int n = 9;
		int threshold = 5;
		int repairIndex = 2;

		// Create original secret share co-efficients and shares
		final BigInteger[] originalCoefficients = Shamir.generateCoefficients(threshold);
		final List<ShamirShare> originalShares = Arrays.asList(Shamir.generateShares(originalCoefficients, n));

		// Create masking coefficients and shares
		final BigInteger[] maskingCoefficients = Shamir.generateCoefficients(threshold, repairIndex);
		final List<ShamirShare> maskingShares = Arrays.asList(Shamir.generateShares(maskingCoefficients, n));

		// Create sum shares
		final Set<ShamirShare> sumShares = new HashSet<>();
		for (int i = 0; i < originalShares.size(); i++) {
			final ShamirShare originalShare = originalShares.get(i);
			final ShamirShare maskingShare = maskingShares.get(i);
			final ShamirShare sumShare = new ShamirShare(originalShare.getX(),
					originalShare.getY().add(maskingShare.getY().mod(r)));
			sumShares.add(sumShare);
		}

		// Interpolate at different points, ensure non-zero at all positions
		// except repair index when using just masking shares
		for (int i = 0; i <= n; i++) {
			if (i == repairIndex) {
				final BigInteger result = Polynomials.interpolateComplete(maskingShares, threshold, i);
				Assert.assertEquals(BigInteger.ZERO, result);
			} else {
				final BigInteger result = Polynomials.interpolateComplete(maskingShares, threshold, i);
				Assert.assertNotEquals(BigInteger.ZERO, result);
			}
		}

		// Interpolate at different points, ensure wrong values at all positions
		// except repair index when using sum shares
		for (int i = 0; i <= n; i++) {
			final BigInteger originalResult = Polynomials.interpolateComplete(originalShares, threshold, i);
			if (i == repairIndex) {
				final BigInteger maskedResult = Polynomials.interpolateComplete(sumShares, threshold, i);
				Assert.assertEquals(originalResult, maskedResult);
			} else {
				final BigInteger maskedResult = Polynomials.interpolateComplete(sumShares, threshold, i);
				Assert.assertNotEquals(originalResult, maskedResult);
			}
		}
	}
	
	

	@Test
	public void testGenerateCoefficientsMultipleMaskingsReconstruction() {

		int n = 9;
		int threshold = 5;
		int repairIndex = 2;

		// Create original secret share co-efficients and shares
		final BigInteger[] originalCoefficients = Shamir.generateCoefficients(threshold);
		final List<ShamirShare> originalShares = Arrays.asList(Shamir.generateShares(originalCoefficients, n));

		// Create first masking coefficients and shares
		final BigInteger[] maskingCoefficients1 = Shamir.generateCoefficients(threshold, repairIndex);
		final List<ShamirShare> maskingShares1 = Arrays.asList(Shamir.generateShares(maskingCoefficients1, n));

		// Create second masking coefficients and shares
		final BigInteger[] maskingCoefficients2 = Shamir.generateCoefficients(threshold, repairIndex);
		final List<ShamirShare> maskingShares2 = Arrays.asList(Shamir.generateShares(maskingCoefficients2, n));
		
		// Create sum shares
		final Set<ShamirShare> sumShares = new HashSet<>();
		for (int i = 0; i < originalShares.size(); i++) {
			final ShamirShare originalShare = originalShares.get(i);
			final ShamirShare maskingShare1 = maskingShares1.get(i);
			final ShamirShare maskingShare2 = maskingShares2.get(i);
			final ShamirShare sumShare = new ShamirShare(originalShare.getX(),
					originalShare.getY().add(maskingShare1.getY()).add(maskingShare2.getY()).mod(r));
			sumShares.add(sumShare);
		}

		// Interpolate at different points, ensure non-zero at all positions
		// except repair index when using just masking shares
		for (int i = 0; i <= n; i++) {
			if (i == repairIndex) {
				final BigInteger result1 = Polynomials.interpolateComplete(maskingShares1, threshold, i);
				Assert.assertEquals(BigInteger.ZERO, result1);
				
				final BigInteger result2 = Polynomials.interpolateComplete(maskingShares2, threshold, i);
				Assert.assertEquals(BigInteger.ZERO, result2);
			} else {
				final BigInteger result = Polynomials.interpolateComplete(maskingShares1, threshold, i);
				Assert.assertNotEquals(BigInteger.ZERO, result);
				
				final BigInteger result2 = Polynomials.interpolateComplete(maskingShares2, threshold, i);
				Assert.assertNotEquals(BigInteger.ZERO, result2);
			}
		}

		// Interpolate at different points, ensure wrong values at all positions
		// except repair index when using sum shares
		for (int i = 0; i <= n; i++) {
			final BigInteger originalResult = Polynomials.interpolateComplete(originalShares, threshold, i);
			
			if (i == repairIndex) {
				final BigInteger maskedResult = Polynomials.interpolateComplete(sumShares, threshold, i);
				Assert.assertEquals(originalResult, maskedResult);
			} else {
				final BigInteger maskedResult = Polynomials.interpolateComplete(sumShares, threshold, i);
				Assert.assertNotEquals(originalResult, maskedResult);
			}
		}
	}

	@Test
	public void testGenerateShares() {

		int n = 9;
		int threshold = 5;

		// Create coefficients
		final BigInteger[] coefficients = Shamir.generateCoefficients(threshold);

		final BigInteger secret = RandomNumberGenerator.generateRandomInteger(r);
		coefficients[0] = secret;

		// Create shares
		final ShamirShare[] shares = Shamir.generateShares(coefficients, n);
		final Set<ShamirShare> shareSet = new HashSet<>(Arrays.asList(shares));

		// Interpolate at different points, secret matches for position 0 only
		for (int i = 0; i <= n; i++) {
			if (i == 0) {
				final BigInteger result = Polynomials.interpolateComplete(shareSet, threshold, i);
				Assert.assertEquals(secret, result);
			} else {
				final BigInteger result = Polynomials.interpolateComplete(shareSet, threshold, i);
				Assert.assertNotEquals(secret, result);
			}
		}
	}

	@Test
	public void testVerifyShamirShareConsistency() {
		fail("Not yet implemented");
	}

	@Test
	public void testComputeSharePublicKeys() {
		fail("Not yet implemented");
	}

	@Test
	public void testVerifyShamirShareConsistencyNoFreeCoefficient() {
		fail("Not yet implemented");
	}

	@Test
	public void testComputeUpdatedPublicKeys() {
		fail("Not yet implemented");
	}

}