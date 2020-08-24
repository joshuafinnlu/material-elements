
package com.zeoflow.material.elements.datepicker;

import com.google.android.material.R;

import android.content.Context;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;
import androidx.annotation.RestrictTo.Scope;
import androidx.core.util.Pair;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import com.zeoflow.material.elements.internal.ManufacturerUtils;
import com.zeoflow.material.elements.internal.ViewUtils;
import com.zeoflow.material.elements.resources.MaterialAttributes;
import com.zeoflow.material.elements.textfield.TextInputLayout;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;


@RestrictTo(Scope.LIBRARY_GROUP)
public class SingleDateSelector implements DateSelector<Long> {

  @Nullable private Long selectedItem;

  @Override
  public void select(long selection) {
    selectedItem = selection;
  }

  private void clearSelection() {
    selectedItem = null;
  }

  @Override
  public void setSelection(@Nullable Long selection) {
    selectedItem = selection == null ? null : UtcDates.canonicalYearMonthDay(selection);
  }

  @Override
  public boolean isSelectionComplete() {
    return selectedItem != null;
  }

  @NonNull
  @Override
  public Collection<Pair<Long, Long>> getSelectedRanges() {
    return new ArrayList<>();
  }

  @NonNull
  @Override
  public Collection<Long> getSelectedDays() {
    ArrayList<Long> selections = new ArrayList<>();
    if (selectedItem != null) {
      selections.add(selectedItem);
    }
    return selections;
  }

  @Override
  @Nullable
  public Long getSelection() {
    return selectedItem;
  }

  @Override
  public View onCreateTextInputView(
      @NonNull LayoutInflater layoutInflater,
      @Nullable ViewGroup viewGroup,
      @Nullable Bundle bundle,
      CalendarConstraints constraints,
      final @NonNull OnSelectionChangedListener<Long> listener) {
    View root = layoutInflater.inflate(R.layout.mtrl_picker_text_input_date, viewGroup, false);

    TextInputLayout dateTextInput = root.findViewById(R.id.mtrl_picker_text_input_date);
    EditText dateEditText = dateTextInput.getEditText();
    if (ManufacturerUtils.isDateInputKeyboardMissingSeparatorCharacters()) {
      
      dateEditText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
    }
    SimpleDateFormat format = UtcDates.getTextInputFormat();
    String formatHint = UtcDates.getTextInputHint(root.getResources(), format);

    if (selectedItem != null) {
      dateEditText.setText(format.format(selectedItem));
    }

    dateEditText.addTextChangedListener(
        new DateFormatTextWatcher(formatHint, format, dateTextInput, constraints) {

          @Override
          void onValidDate(@Nullable Long day) {
            if (day == null) {
              clearSelection();
            } else {
              select(day);
            }
            listener.onSelectionChanged(getSelection());
          }

          @Override
          void onInvalidDate() {
            listener.onIncompleteSelectionChanged();
          }
        });

    ViewUtils.requestFocusAndShowKeyboard(dateEditText);

    return root;
  }

  @Override
  public int getDefaultThemeResId(Context context) {
    return MaterialAttributes.resolveOrThrow(
        context, R.attr.materialCalendarTheme, MaterialDatePicker.class.getCanonicalName());
  }

  @NonNull
  @Override
  public String getSelectionDisplayString(@NonNull Context context) {
    Resources res = context.getResources();
    if (selectedItem == null) {
      return res.getString(R.string.mtrl_picker_date_header_unselected);
    }
    String startString = DateStrings.getYearMonthDay(selectedItem);
    return res.getString(R.string.mtrl_picker_date_header_selected, startString);
  }

  @Override
  public int getDefaultTitleResId() {
    return R.string.mtrl_picker_date_header_title;
  }

  

  
  public static final Parcelable.Creator<SingleDateSelector> CREATOR =
      new Parcelable.Creator<SingleDateSelector>() {
        @NonNull
        @Override
        public SingleDateSelector createFromParcel(@NonNull Parcel source) {
          SingleDateSelector singleDateSelector = new SingleDateSelector();
          singleDateSelector.selectedItem = (Long) source.readValue(Long.class.getClassLoader());
          return singleDateSelector;
        }

        @NonNull
        @Override
        public SingleDateSelector[] newArray(int size) {
          return new SingleDateSelector[size];
        }
      };

  @Override
  public int describeContents() {
    return 0;
  }

  @Override
  public void writeToParcel(@NonNull Parcel dest, int flags) {
    dest.writeValue(selectedItem);
  }
}
