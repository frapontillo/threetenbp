/*
 * Copyright (c) 2007-2009 Stephen Colebourne & Michael Nascimento Santos
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  * Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 *
 *  * Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 *  * Neither the name of JSR-310 nor the names of its contributors
 *    may be used to endorse or promote products derived from this software
 *    without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package javax.time.calendar;

import java.io.Serializable;
import java.lang.ref.SoftReference;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import javax.time.calendar.format.DateTimeFormatterBuilder.TextStyle;
import javax.time.period.PeriodUnit;

/**
 * The rule defining how a measurable field of time operates.
 * <p>
 * Time field rule implementations define how a field like 'day of month' operates.
 * This includes the field name and minimum/maximum values.
 * <p>
 * DateTimeFieldRule is an abstract class and must be implemented with care to
 * ensure other classes in the framework operate correctly.
 * All instantiable subclasses must be final, immutable and thread-safe and must
 * ensure serialization works correctly.
 *
 * @author Michael Nascimento Santos
 * @author Stephen Colebourne
 */
public abstract class DateTimeFieldRule implements Comparable<DateTimeFieldRule>, Serializable {

    /** A Math context for calculating fractions from values. */
    private static final MathContext FRACTION_CONTEXT = new MathContext(9, RoundingMode.FLOOR);
    /** A Math context for calculating values from fractions. */
    private static final MathContext VALUE_CONTEXT = new MathContext(0, RoundingMode.FLOOR);

    /** The name of the rule, not null. */
    private final Chronology chronology;
    /** The id of the rule, not null. */
    private final String id;
    /** The name of the rule, not null. */
    private final String name;
    /** The period unit, not null. */
    private final PeriodUnit periodUnit;
    /** The period range, not null. */
    private final PeriodUnit periodRange;
    /** The minimum value for the field. */
    private final int minimumValue;
    /** The maximum value for the field. */
    private final int maximumValue;
    /** The cached text for this rule. */
    private final ConcurrentMap<Locale, SoftReference<EnumMap<TextStyle, TextStore>>> textStores;

    /**
     * Constructor.
     *
     * @param chronology  the chronology, not null
     * @param name  the name of the type, not null
     * @param periodUnit  the period unit, not null
     * @param periodRange  the period range, not null
     * @param minimumValue  the minimum value
     * @param maximumValue  the minimum value
     */
    protected DateTimeFieldRule(
            Chronology chronology,
            String name,
            PeriodUnit periodUnit,
            PeriodUnit periodRange,
            int minimumValue,
            int maximumValue) {
        this(chronology, name, periodUnit, periodRange, minimumValue, maximumValue, false);
    }

    /**
     * Constructor.
     *
     * @param chronology  the chronology, not null
     * @param name  the name of the type, not null
     * @param periodUnit  the period unit, not null
     * @param periodRange  the period range, not null
     * @param minimumValue  the minimum value
     * @param maximumValue  the minimum value
     * @param hasText  true if this field has a text representation
     */
    protected DateTimeFieldRule(
            Chronology chronology,
            String name,
            PeriodUnit periodUnit,
            PeriodUnit periodRange,
            int minimumValue,
            int maximumValue,
            boolean hasText) {
        if (chronology == null) {
            throw new NullPointerException("The chronology must not be null");
        }
        if (name == null) {
            throw new NullPointerException("The name must not be null");
        }
//        if (periodUnit == null) {
//            throw new NullPointerException("periodUnit must not be null");
//        }
//        if (periodRange == null) {
//            throw new NullPointerException("periodRange must not be null");
//        }
        this.chronology = chronology;
        this.id = chronology.getName() + '.' + name;
        this.name = name;
        this.periodUnit = periodUnit;
        this.periodRange = periodRange;
        this.minimumValue = minimumValue;
        this.maximumValue = maximumValue;
        this.textStores = (hasText ? new ConcurrentHashMap<Locale, SoftReference<EnumMap<TextStyle, TextStore>>>() : null);
    }

    //-----------------------------------------------------------------------
    /**
     * Gets the id of the field.
     * <p>
     * The id is of the form 'ChronologyName.FieldName'.
     * No two fields should have the same id.
     *
     * @return the id of the field, never null
     */
    public final String getID() {
        return id;
    }

    /**
     * Gets the name of the field.
     * <p>
     * Subclasses should use the form 'UnitOfRange' whenever possible.
     *
     * @return the name of the field, never null
     */
    public String getName() {
        return name;
    }

    //-----------------------------------------------------------------------
    /**
     * Gets the period unit, which the element which alters within the range.
     * <p>
     * In the phrase 'hour of day', the unit is the hour.
     *
     * @return the rule for the unit period, never null
     */
    public PeriodUnit getPeriodUnit() {
        return periodUnit;
    }

    /**
     * Gets the period range, which the field is bound by.
     * <p>
     * In the phrase 'hour of day', the range is the day.
     *
     * @return the rule for the range period, null if unbounded
     */
    public PeriodUnit getPeriodRange() {
        return periodRange;
    }

    //-----------------------------------------------------------------------
    /**
     * Checks if the this field is supported for the specified date and time.
     *
     * @param date  the date, may be null
     * @param time  the time, may be null
     * @return true if the field is supported
     */
    public final boolean isSupported(LocalDate date, LocalTime time) {
        return (getValueQuiet(date, time) != null);
    }

    /**
     * Gets the value for this field throwing an exception if the field cannot be obtained.
     * <p>
     * The value will be checked for basic validity.
     * The value returned will be within the valid range for the field.
     * Also, if the value is present in both the date/time and the field-value
     * map then the two values must be the same.
     *
     * @param calendricalProvider  the calendrical provider, not null
     * @return the value of the field
     * @throws UnsupportedCalendarFieldException if the value cannot be extracted
     */
    public int getValue(CalendricalProvider calendricalProvider) {
        int value = calendricalProvider.toCalendrical().deriveValue(this);
        checkValue(value);
        return value;
    }

    /**
     * Gets the value of this field from the date or time specified.
     *
     * @param date  the date, may be null
     * @param time  the time, may be null
     * @return the value of the field
     * @throws UnsupportedCalendarFieldException if the value cannot be extracted
     */
    public final int getValue(LocalDate date, LocalTime time) {
        Integer value = getValueQuiet(date, time);
        if (value == null) {
            throw new UnsupportedCalendarFieldException(this);
        }
        return value;
    }

    /**
     * Gets the value of this field from the date or time specified.
     * <p>
     * A typical implementation of this method checks for null and calculates
     * the value. For example, here is an implementation for the year field:
     * <pre>
     * return (date == null ? null : date.getYear().getValue());
     * </pre>
     *
     * @param date  the date, may be null
     * @param time  the time, may be null
     * @return the value of the field, null if unable to derive field
     */
    public Integer getValueQuiet(LocalDate date, LocalTime time) {
        return null;  // override if field can be derived
    }

    /**
     * Gets the value of this field from the map of field-value pairs specified.
     * <p>
     * This method queries the map to determine if it holds a value for this field.
     * If it does, then the value is returned.
     * Otherwise, an attempt is made to {@link #deriveValue derive}
     * the value from the value of other fields in the map.
     *
     * @param calendricalFieldMap  the calendrical to derive from, not null
     * @return the value of the field, null if unable to derive field
     */
    public final Integer getValueQuiet(Calendrical.FieldMap calendricalFieldMap) {
        Integer value = calendricalFieldMap.getQuiet(this);
        return (value == null ? deriveValue(calendricalFieldMap) : value);
    }

    /**
     * Derives the value of this field from the specified calendrical.
     * <p>
     * This method derives the value for this field from other fields in the map.
     * The implementation does not check if the map already contains a value for this field.
     * For example, if this field is QuarterOfYear, then the value can be derived
     * from MonthOfYear. The implementation must not check to see of the map
     * already contains a value for QuarterOfYear.
     * <p>
     * The derivation can be recursive depending on the hierarchy of fields.
     * This is achieved by using {@link #getValueQuiet} to obtain the parent field rule.
     * <p>
     * A typical implementation of this method obtains the parent value and performs a calculation.
     * For example, here is a simple implementation for the QuarterOfYear field
     * (which doesn't handle negative numbers or leniency):
     * <pre>
     * Integer moyVal = ISOChronology.monthOfYearRule().getValueQuiet(fieldValueMap);
     * return (moyVal == null ? null : ((moyVal - 1) % 4) + 1);
     * </pre>
     * Extracts the value for this field using information in the field map.
     * <p>
     * This method is designed to be overridden in subclasses.
     * The subclass implementation must be thread-safe.
     *
     * @param calendricalFieldMap  the calendrical to derive from, not null
     * @return the derived value, null if unable to derive
     */
    protected Integer deriveValue(Calendrical.FieldMap calendricalFieldMap) {
        return null;  // do nothing - override if this field can derive
    }

    /**
     * Merges this field with other fields to form higher level fields.
     * <p>
     * The aim of this method is to assist in the process of extracting the most
     * date-time information possible from a map of field-value pairs.
     * The merging process is controlled by the mutable merger instance and
     * the input and output of the this merge are held there.
     * <p>
     * Subclasses that override this method may use methods on the merger to
     * obtain the values to merge. The value is guaranteed to be available for
     * this field if this method is called.
     * <p>
     * If the override successfully merged some fields then the following must be performed.
     * The merged field must be stored using {@link Calendrical.Merger#storeMergedField}.
     * Each field used in the merge must be marked as being used by calling
     * {@link Calendrical.Merger#markFieldAsProcessed}.
     * <p>
     * An example to merge two fields into one - hour of AM/PM and AM/PM:
     * <pre>
     *  Integer hapVal = merger.getValue(ISOChronology.hourOfAmPmRule());
     *  if (hapVal != null) {
     *    int amPm = merger.getValueInt(this);
     *    int hourOfDay = MathUtils.safeAdd(MathUtils.safeMultiply(amPm, 12), hapVal);
     *    merger.storeMergedField(ISOChronology.hourOfDayRule(), hourOfDay);
     *    merger.markFieldAsProcessed(this);
     *    merger.markFieldAsProcessed(ISOChronology.hourOfAmPmRule());
     *  }
     * </pre>
     *
     * @param merger  the merger instance controlling the merge process, not null
     */
    protected void mergeFields(Calendrical.Merger merger) {
        // do nothing - override if this field can merge to a more significant field
    }

    /**
     * Merges this field with other fields to form a date or time.
     * <p>
     * The aim of this method is to assist in the process of extracting the most
     * date-time information possible from a map of field-value pairs.
     * The merging process is controlled by the mutable merger instance and
     * the input and output of the this merge are held there.
     * <p>
     * Subclasses that override this method may use methods on the merger to
     * obtain the values to merge. The value is guaranteed to be available for
     * this field if this method is called.
     * <p>
     * If the override successfully merged some fields then the following must be performed.
     * A merged date must be stored using {@link Calendrical.Merger#storeMergedDate(LocalDate)}.
     * A merged time must be stored using {@link Calendrical.Merger#storeMergedTime(LocalTime)}
     * if the merge is strict, or {@link Calendrical.Merger#storeMergedTime(javax.time.calendar.LocalTime.Overflow)}
     * if the merge is lenient.
     * Each field used in the merge must be marked as being used by calling
     * {@link Calendrical.Merger#markFieldAsProcessed}.
     * <p>
     * An example to merge three fields into a date - year, month and day:
     * <pre>
     *  Integer moyVal = merger.getValue(ISOChronology.monthOfYearRule());
     *  Integer domVal = merger.getValue(ISOChronology.dayOfMonthRule());
     *  if (moyVal != null && domVal != null) {
     *    int year = merger.getValueInt(this);
     *    LocalDate date = merger.getContext().resolveDate(year, moyVal, domVal);
     *    merger.storeMergedDate(date);
     *    merger.markFieldAsProcessed(this);
     *    merger.markFieldAsProcessed(ISOChronology.monthOfYearRule());
     *    merger.markFieldAsProcessed(ISOChronology.dayOfMonthRule());
     *  }
     * </pre>
     *
     * @param merger  the merger instance controlling the merge process, not null
     */
    protected void mergeDateTime(Calendrical.Merger merger) {
        // do nothing - override if this field can merge to a date/time
    }

    //-----------------------------------------------------------------------
    /**
     * Checks if the value is valid or invalid for this field.
     * <p>
     * This method has no knowledge of other calendrical fields, thus only the
     * outer minimum and maximum range for the field is validated.
     * <p>
     * This method performs the same check as {@link #isValidValue(long)}.
     *
     * @param value  the value to check
     * @return true if the value is valid, false if invalid
     */
    public boolean isValidValue(int value) {
        return (value >= getMinimumValue() && value <= getMaximumValue());
    }

    /**
     * Checks if the value is valid or invalid for this field.
     * <p>
     * This method has no knowledge of other calendrical fields, thus only the
     * outer minimum and maximum range for the field is validated.
     * <p>
     * This method performs the same check as {@link #isValidValue(int)}.
     *
     * @param value  the value to check
     * @return true if the value is valid, false if invalid
     */
    public boolean isValidValue(long value) {
        return (value >= getMinimumValue() && value <= getMaximumValue());
    }

    //-----------------------------------------------------------------------
    /**
     * Checks if the value is invalid and throws an exception if it is.
     * <p>
     * This method has no knowledge of other calendrical fields, thus only the
     * outer minimum and maximum range for the field is validated.
     * <p>
     * This method performs the same check as {@link #checkValue(long)}.
     * The implementation uses {@link #isValidValue(int)}.
     *
     * @param value  the value to check
     * @throws IllegalCalendarFieldValueException if the value is invalid
     */
    public void checkValue(int value) {
        if (isValidValue(value) == false) {
            throw new IllegalCalendarFieldValueException(this, value, getMinimumValue(), getMaximumValue());
        }
    }

    /**
     * Checks if the value is invalid and throws an exception if it is.
     * <p>
     * This method has no knowledge of other calendrical fields, thus only the
     * outer minimum and maximum range for the field is validated.
     * <p>
     * This method performs the same check as {@link #checkValue(int)}.
     * The implementation uses {@link #isValidValue(long)}.
     *
     * @param value  the value to check
     * @return the value cast to an int
     * @throws IllegalCalendarFieldValueException if the value is invalid
     */
    public int checkValue(long value) {
        if (isValidValue(value) == false) {
            throw new IllegalCalendarFieldValueException(this, value, getMinimumValue(), getMaximumValue());
        }
        return (int) value;
    }

    //-----------------------------------------------------------------------
    /**
     * Is the set of values, from the minimum value to the maximum, a fixed
     * set, or does it vary according to other fields.
     *
     * @return true if the set of values is fixed
     */
    public boolean isFixedValueSet() {
        return getMaximumValue() == getSmallestMaximumValue() &&
                getMinimumValue() == getLargestMinimumValue();
    }

    //-----------------------------------------------------------------------
    /**
     * Gets the minimum value that the field can take.
     *
     * @return the minimum value for this field
     */
    public int getMinimumValue() {
        return minimumValue;
    }

    /**
     * Gets the largest possible minimum value that the field can take.
     * <p>
     * The default implementation returns {@link #getMinimumValue()}.
     * Subclasses must override this as necessary.
     *
     * @return the largest possible minimum value for this field
     */
    public int getLargestMinimumValue() {
        return getMinimumValue();
    }

    /**
     * Gets the minimum value that the field can take using the specified
     * calendrical information to refine the accuracy of the response.
     * <p>
     * The result of this method may still be inaccurate, if there is insufficient
     * information in the calendrical.
     * <p>
     * The default implementation returns {@link #getMinimumValue()}.
     * Subclasses must override this as necessary.
     *
     * @param calendrical  context calendrical, not null
     * @return the minimum value of the field given the context
     */
    public int getMinimumValue(Calendrical calendrical) {
        return getMinimumValue();
    }

    //-----------------------------------------------------------------------
    /**
     * Gets the maximum value that the field can take.
     *
     * @return the maximum value for this field
     */
    public int getMaximumValue() {
        return maximumValue;
    }

    /**
     * Gets the smallest possible maximum value that the field can take.
     * <p>
     * The default implementation returns {@link #getMaximumValue()}.
     * Subclasses must override this as necessary.
     *
     * @return the smallest possible maximum value for this field
     */
    public int getSmallestMaximumValue() {
        return getMaximumValue();
    }

    /**
     * Gets the minimum value that the field can take using the specified
     * calendrical information to refine the accuracy of the response.
     * <p>
     * The result of this method will still be inaccurate if there is insufficient
     * information in the calendrical.
     * <p>
     * For example, if this field is the ISO day of month field, then the number
     * of days in the month varies depending on the month and year. If both the
     * month and year can be derived from the calendrical, then the maximum value
     * returned will be accurate. Otherwise the 'best guess' value from
     * {@link #getMaximumValue()} will be returned.
     * <p>
     * The default implementation returns {@link #getMaximumValue()}.
     * Subclasses must override this as necessary.
     *
     * @param calendrical  context calendrical, not null
     * @return the minimum value of the field given the context
     */
    public int getMaximumValue(Calendrical calendrical) {
        return getMaximumValue();
    }

    //-----------------------------------------------------------------------
    /**
     * Gets the text for this field.
     * <p>
     * Some fields have a textual representation, such as day of week or
     * month of year. This method provides a convenient way to convert a value
     * to such a textual representation.
     * More control is available using {@link #getTextStore}.
     * <p>
     * If there is no textual mapping, then the value is returned as per
     * {@link Integer#toString()}. Note that this is different to what occurs
     * in printing /parsing, where a more advanced localized conversion from
     * int to String is used.
     *
     * @param value  the value to convert to text, not null
     * @param locale  the locale to use, not null
     * @param textStyle  the text style, not null
     * @return the text of the field, never null
     */
    public String getText(int value, Locale locale, TextStyle textStyle) {
        TextStore textStore = getTextStore(locale, textStyle);
        String text = (textStore != null ? textStore.getValueText(value) : null);
        return text == null ? Integer.toString(value) : text;
    }

    /**
     * Gets the text map for this field with the specified locale and style.
     * <p>
     * Some fields have a textual representation, such as day of week or
     * month of year. The text store provides details of those textual representations.
     * <p>
     * To supply text, subclasses should pass true in the constructor and
     * override {@link #createTextStore}. This method is not normally overridden.
     *
     * @param locale  the locale to use, not null
     * @param textStyle  the text style, not null
     * @return the text cache, null if no text available
     */
    public TextStore getTextStore(Locale locale, TextStyle textStyle) {
        if (textStores == null) {
            return null;
        }
        SoftReference<EnumMap<TextStyle, TextStore>> ref = textStores.get(locale);
        if (ref != null) {
            EnumMap<TextStyle, TextStore> textMapByStyle = ref.get();
            if (textMapByStyle != null) {
                return textMapByStyle.get(textStyle);
            }
        }
        EnumMap<TextStyle, TextStore> textStoreByStyle = new EnumMap<TextStyle, TextStore>(TextStyle.class);
        createTextStores(textStoreByStyle, locale);
        textStoreByStyle = new EnumMap<TextStyle, TextStore>(textStoreByStyle);
        textStores.put(locale, new SoftReference<EnumMap<TextStyle, TextStore>>(textStoreByStyle));
        return textStoreByStyle.get(textStyle);
    }

    /**
     * Creates the text store for each style for the specified locale.
     * <p>
     * It is intended that a new copy of the text store should be created in
     * response to calling this method as the result is cached by {@link #getTextStore}.
     *
     * @param textStores  the map to populate with TextStore instances, not null
     * @param locale  the locale to use, not null
     */
    protected void createTextStores(EnumMap<TextStyle, TextStore> textStores, Locale locale) {
        // do nothing - override if field provides text
    }
 
    //-----------------------------------------------------------------------
    /**
     * Converts a value for this field to a fraction between 0 and 1.
     * <p>
     * The fractional value is between 0 (inclusive) and 1 (exclusive).
     * It can only be returned if {@link #isFixedValueSet()} returns true and the
     * {@link #getMinimumValue()} returns zero.
     * The fraction is obtained by calculation from the field range using 9 decimal
     * places and a rounding mode of {@link RoundingMode#FLOOR FLOOR}.
     * <p>
     * For example, the second of minute value of 15 would be returned as 0.25,
     * assuming the standard definition of 60 seconds in a minute.
     *
     * @param value  the value to convert, not null
     * @return the fractional value of the field
     * @throws UnsupportedCalendarFieldException if the value cannot be converted
     * @throws IllegalCalendarFieldValueException if the value is invalid
     */
    public BigDecimal convertValueToFraction(int value) {
        if (isFixedValueSet() == false) {
            throw new UnsupportedCalendarFieldException(this, "The fractional value of " + getName() +
                    " cannot be obtained as the range is not fixed");
        }
        if (getMinimumValue() != 0) {
            throw new UnsupportedCalendarFieldException(this, "The fractional value of " + getName() +
                    " cannot be obtained as the minimum field value is not zero");
        }
        checkValue(value);
        long range = getMaximumValue();
        range++;
        BigDecimal decimal = new BigDecimal(value);
        return decimal.divide(new BigDecimal(range), FRACTION_CONTEXT);
    }

    /**
     * Converts a fraction from 0 to 1 for this field to a value.
     * <p>
     * The fractional value must be between 0 (inclusive) and 1 (exclusive).
     * It can only be returned if {@link #isFixedValueSet()} returns true and the
     * {@link #getMinimumValue()} returns zero.
     * The value is obtained by calculation from the field range and a rounding
     * mode of {@link RoundingMode#FLOOR FLOOR}.
     * <p>
     * For example, the fractional second of minute of 0.25 would be converted to 15,
     * assuming the standard definition of 60 seconds in a minute.
     *
     * @param fraction  the fraction to convert, not null
     * @return the value of the field, checked for validity
     * @throws UnsupportedCalendarFieldException if the value cannot be converted
     * @throws IllegalCalendarFieldValueException if the value is invalid
     */
    public int convertFractionToValue(BigDecimal fraction) {
        if (isFixedValueSet() == false) {
            throw new UnsupportedCalendarFieldException(this, "The fractional value of " + getName() +
                    " cannot be converted as the range is not fixed");
        }
        if (getMinimumValue() != 0) {
            throw new UnsupportedCalendarFieldException(this, "The fractional value of " + getName() +
                    " cannot be converted as the minimum field value is not zero");
        }
        long range = getMaximumValue();
        range++;
        BigDecimal decimal = fraction.multiply(new BigDecimal(range), VALUE_CONTEXT);
        try {
            int value = decimal.intValueExact();
            checkValue(value);
            return value;
        } catch (ArithmeticException ex) {
            throw new IllegalCalendarFieldValueException("The fractional value " + fraction + " of " + getName() +
                    " cannot be converted as it is not in the range 0 (inclusive) to 1 (exclusive)", this);
        }
    }

    //-----------------------------------------------------------------------
    /**
     * Compares this DateTimeFieldRule to another based on the period unit
     * followed by the period range followed by the chronology name.
     * <p>
     * The period unit is compared first, so MinuteOfHour will be less than
     * HourOfDay, which will be less than DayOfWeek. When the period unit is
     * the same, the period range is compared, so DayOfWeek is less than
     * DayOfMonth, which is less than DayOfYear. Finally, the chronology name
     * is compared.
     *
     * @param other  the other type to compare to, not null
     * @return the comparator result, negative if less, postive if greater, zero if equal
     * @throws NullPointerException if other is null
     */
    public int compareTo(DateTimeFieldRule other) {
        int cmp = this.getPeriodUnit().compareTo(other.getPeriodUnit());
        if (cmp != 0) {
            return cmp;
        }
        if (this.getPeriodRange() == other.getPeriodRange()) {
            return chronology.getName().compareTo(other.chronology.getName());
        }
        if (this.getPeriodRange() == null) {
            return 1;
        }
        if (other.getPeriodRange() == null) {
            return -1;
        }
        cmp = this.getPeriodRange().compareTo(other.getPeriodRange());
        if (cmp != 0) {
            return cmp;
        }
        return chronology.getName().compareTo(other.chronology.getName());
    }

    //-----------------------------------------------------------------------
    /**
     * Returns a string representation of the rule.
     *
     * @return a description of the rule
     */
    @Override
    public String toString() {
        return getID();
    }

    //-----------------------------------------------------------------------
    /**
     * The mapping between integer values and textual representations.
     * <p>
     * Some fields have a textual representation, such as day of week or month of year.
     * These textual representations can be captured in this class for printing
     * and parsing.
     * <p>
     * TextStore is immutable and thread-safe.
     *
     * @author Stephen Colebourne
     */
    public static final class TextStore {
        /**
         * The locale of the text.
         */
        private final Locale locale;
        /**
         * Map of value to text.
         */
        private final Map<Integer, String> valueTextMap;
        /**
         * Map of case sensitive text to value.
         */
        private final Map<String, Integer> textValueMap;
        /**
         * Map of case insensitive text to value.
         */
        private final Map<String, Integer> insensitiveTextValueMap;
        /**
         * The lengths of the text items.
         */
        private final int[] lengths;

        //-----------------------------------------------------------------------
        /**
         * Constructor.
         *
         * @param locale  the locale, not null
         * @param valueTextMap  the map of values to text to store, not null
         */
        public TextStore(Locale locale, Map<Integer, String> valueTextMap) {
            ISOChronology.checkNotNull(locale, "Locale must not be null");
            ISOChronology.checkNotNull(valueTextMap, "Map must not be null");
            if (valueTextMap.containsKey(null) || valueTextMap.containsValue(null) || valueTextMap.containsValue("")) {
                throw new IllegalArgumentException("The map must not contain null or empty text");
            }
            this.locale = locale;
            Map<Integer, String> copy = new HashMap<Integer, String>(valueTextMap);
            Map<String, Integer> reverse = new HashMap<String, Integer>();
            Map<String, Integer> insensitive = new HashMap<String, Integer>();
            Set<Integer> lengthSet = new HashSet<Integer>();
            for (Entry<Integer, String> entry : copy.entrySet()) {
                String text = entry.getValue();
                Integer value = entry.getKey();
                reverse.put(text, value);
                lengthSet.add(text.length());
                String lower = text.toLowerCase(locale);
                insensitive.put(lower, value);
                lengthSet.add(lower.length());
                String upper = text.toUpperCase(locale);
                insensitive.put(upper, value);
                lengthSet.add(upper.length());
            }
            if (reverse.size() < copy.size()) {
                // duplicate text for a given value, so parsing is not supported
                this.textValueMap = Collections.emptyMap();
                this.insensitiveTextValueMap = Collections.emptyMap();
                this.lengths = null;
            } else {
                textValueMap = Collections.unmodifiableMap(reverse);
                insensitiveTextValueMap = Collections.unmodifiableMap(insensitive);
                this.lengths = new int[lengthSet.size()];
                int i = 0;
                for (Iterator<Integer> it = lengthSet.iterator(); it.hasNext(); ) {
                    lengths[i++] = it.next();
                }
                Arrays.sort(lengths);
            }
            this.valueTextMap = Collections.unmodifiableMap(copy);
        }

        //-----------------------------------------------------------------------
        /**
         * Gets the locale that the text relates to.
         *
         * @return the locale for the text, never null
         */
        public Locale getLocale() {
            return locale;
        }

        //-----------------------------------------------------------------------
        /**
         * Gets the map of text for each integer value.
         *
         * @return the unmodifiable map of value to text, never null
         */
        public Map<Integer, String> getValueTextMap() {
            return valueTextMap;
        }

        /**
         * Gets the text for the specified integer value.
         * <p>
         * The text associated with the value is returned, or null if none found.
         *
         * @param value  the value to get text for
         * @return the text for the field value, null if no text found
         */
        public String getValueText(int value) {
            return valueTextMap.get(value);
        }

        //-----------------------------------------------------------------------
        /**
         * Gets the derived map expressing the value for each text.
         * <p>
         * If the value-text map contains duplicate text elements then this map
         * will be empty.
         *
         * @return the unmodifiable map of text to value for the field rule and style, never null
         */
        public Map<String, Integer> getTextValueMap() {
            return textValueMap;
        }

        /**
         * Matches the specified text against the text-value map returning the
         * matched length and value.
         * <p>
         * This method is intended for use during parsing, and matches the search text
         * against the text-value map, optionally ignoring case.
         *
         * @param ignoreCase  true to ignore case during the matching
         * @param parseText  the text to match against
         * @return a long packed result of two int values (for performance in parsing).
         *  The value is <code>(parseLength << 32 + matchedValue)</code>.
         *  Zero is returned if there is no match.
         *  Minus one is returned if the text store cannot parse.
         *  The parse length can be obtained via (result >>> 32).
         *  The value can be obtained via ((int) result).
         */
        public long matchText(boolean ignoreCase, String parseText) {
            ISOChronology.checkNotNull(parseText, "Search text must not be null");
            if (lengths == null) {
                return -1;
            }
            int lengthsStart = Arrays.binarySearch(lengths, parseText.length());
            lengthsStart = (lengthsStart < 0 ? -lengthsStart - 2 : lengthsStart);
            if (ignoreCase) {
                parseText = parseText.toUpperCase(locale);
                for (int i = lengthsStart; i >= 0; i--) {
                    Integer value = insensitiveTextValueMap.get(parseText.substring(0, lengths[i]));
                    if (value != null) {
                        return (((long) lengths[i]) << 32) + value;
                    }
                }
                parseText = parseText.toLowerCase(locale);
                for (int i = lengthsStart; i >= 0; i--) {
                    Integer value = insensitiveTextValueMap.get(parseText.substring(0, lengths[i]));
                    if (value != null) {
                        return (((long) lengths[i]) << 32) + value;
                    }
                }
            } else {
                for (int i = lengthsStart; i >= 0; i--) {
                    Integer value = textValueMap.get(parseText.substring(0, lengths[i]));
                    if (value != null) {
                        return (((long) lengths[i]) << 32) + value;
                    }
                }
            }
            return 0;
        }

    }

}
