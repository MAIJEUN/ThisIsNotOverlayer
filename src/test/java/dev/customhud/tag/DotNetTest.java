package dev.customhud.tag;

import dev.customhud.tag.compile.DotNet;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Expected strings are what .NET 8 prints (ko-KR culture for percent/currency). */
class DotNetTest {
	private static String f(Object v, String fmt) {
		return DotNet.format(v, fmt);
	}

	@Test
	void toStringMatchesDotNet() {
		assertEquals("1557", DotNet.toString(1557.0));
		assertEquals("0.1", DotNet.toString(0.1));
		assertEquals("0.30000000000000004", DotNet.toString(0.1 + 0.2));
		assertEquals("12.3456", DotNet.toString(12.3456));
		assertEquals("0.0001", DotNet.toString(0.0001));
		assertEquals("1E-05", DotNet.toString(0.00001));
		assertEquals("1E+15", DotNet.toString(1e15));
		assertEquals("123456789012345", DotNet.toString(123456789012345.0));
		assertEquals("1.2345678901234568E+17", DotNet.toString(123456789012345678.0));
		assertEquals("-2.5", DotNet.toString(-2.5));
		assertEquals("-0", DotNet.toString(-0.0));
		assertEquals("NaN", DotNet.toString(Double.NaN));
		assertEquals("∞", DotNet.toString(Double.POSITIVE_INFINITY));
		assertEquals("0.1", DotNet.toString(0.1f));
		assertEquals("1E+07", DotNet.toString(1e7f));
		assertEquals("60", DotNet.toString(60));
		assertEquals("True", DotNet.toString(true));
		assertEquals("False", DotNet.toString(false));
	}

	@Test
	void standardFormats() {
		assertEquals("1557.0", f(1557.0, "F1"));
		assertEquals("1,557", f(1557.0, "N0"));
		assertEquals("1,234,567.89", f(1234567.891, "N"));
		assertEquals("12.3%", f(0.123, "P1"));
		assertEquals("50.00%", f(0.5, "P"));
		assertEquals("0.13", f(0.125, "F2"));
		assertEquals("3", f(2.5, "F0"));
		assertEquals("1.00", f(1.005, "F2"));
		assertEquals("1.23E+002", f(123.456, "E2"));
		assertEquals("1.234560e+002", f(123.456, "e"));
		assertEquals("1.23E+04", f(12345.678, "G3"));
		assertEquals("0.0001234", f(0.0001234, "G4"));
		assertEquals("12345.678", f(12345.678, "G"));
		assertEquals("00042", f(42, "D5"));
		assertEquals("-00042", f(-42, "D5"));
		assertEquals("FF", f(255, "X"));
		assertEquals("00ff", f(255, "x4"));
		assertEquals("₩1,235", f(1234.5, "C"));
		assertEquals("-3.50", f(-3.5, "F2"));
		assertThrows(IllegalArgumentException.class, () -> f(1.0, "D5"));
		assertThrows(IllegalArgumentException.class, () -> f(1.0, "X"));
		assertThrows(IllegalArgumentException.class, () -> f(1.0, "K"));
	}

	@Test
	void customFormats() {
		assertEquals("1234.57", f(1234.5678, "0.00"));
		assertEquals("1,234.57", f(1234.5678, "#,##0.00"));
		assertEquals("1,234,567", f(1234567, "#,##0"));
		assertEquals("50%", f(0.5, "0%"));
		assertEquals("", f(0, "#"));
		assertEquals("0", f(0, "0"));
		assertEquals("05", f(5, "00"));
		assertEquals("12.3", f(12.3, "0.##"));
		assertEquals("12", f(12.0, "0.##"));
		assertEquals("0.5", f(0.5, "0.##"));
		assertEquals(".5", f(0.5, "#.##"));
		assertEquals("1", f(1234567, "0,,"));
		assertEquals("1,235K", f(1234567, "#,##0,K"));
		assertEquals("(5)", f(-5, "0;(0)"));
		assertEquals("zero", f(0, "0;-0;zero"));
		assertEquals("-5", f(-5, "0"));
		assertEquals("1.2E+03", f(1234.5, "0.0E+00"));
		assertEquals("1.23e4", f(12345, "0.00e0"));
		assertEquals("0-05", f(5, "0-00"));
		assertEquals("1-23", f(123, "0-00"));
		assertEquals("FPS 60", f(60.0, "FPS 0"));
		assertEquals("0 ms", f(0.4, "0 'ms'"));
		assertEquals("60.0", f(60.0, "0.0"));
		assertEquals("0.30000000000000", f(0.1 + 0.2, "0.00000000000000"));
	}
}
