/*
 * Not sure if I need a copyright.
 * Not sure how to claim a copyright
 */

package com.android.settings;

/* Need to import the APIs that control and listen to the parts I need to work with
 */

/* Need to create a method to find the user's intentions, I'm thinking a drop down menu.*/
public void onCheckboxClicked(View view) {
//Was the box checked?
    boolean checked = ((CheckBox) view).isChecked();

    switch(view.getId()) {
        case R.id.enable:
            if (checked)
                // Start the data control
            else
                // Do nothing
            break;
     }
}
