package net.sf.briar.util;

import java.util.Arrays;

import junit.framework.TestCase;

import org.junit.Test;

public class StringUtilsTest extends TestCase {

	@Test
	public void testHead() {
		String head = StringUtils.head("123456789", 5);
		assertEquals("12345...", head);
	}

	@Test
	public void testTail() {
		String tail = StringUtils.tail("987654321", 5);
		assertEquals("...54321", tail);
	}

	@Test
	public void testToHexString() {
		byte[] b = new byte[] {1, 2, 3, 127, -128};
		String s = StringUtils.toHexString(b);
		assertEquals("0102037F80", s);
	}

	@Test
	public void testFromHexString() {
		try {
			StringUtils.fromHexString("12345");
			fail();
		} catch(IllegalArgumentException expected) {}
		try {
			StringUtils.fromHexString("ABCDEFGH");
			fail();
		} catch(IllegalArgumentException expected) {}
		byte[] b = StringUtils.fromHexString("0102037F80");
		assertTrue(Arrays.equals(new byte[] {1, 2, 3, 127, -128}, b));
		b = StringUtils.fromHexString("0a0b0c0d0e0f");
		assertTrue(Arrays.equals(new byte[] {10, 11, 12, 13, 14, 15}, b));
	}
}
