package chakib.com.instantchat;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.RatingBar;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Created by Chakib on 08/10/2017.
 */

public class ConnectFragment extends android.support.v4.app.DialogFragment {
    TextView errorMessage;
    Button okButton;

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_connexion, container, false);
        getDialog().setTitle("Connection Error");
        errorMessage = (TextView) view.findViewById(R.id.ErrorTextView);
        okButton = (Button) view.findViewById(R.id.ok_button);
        okButton.setOnClickListener(new OnGetRatingClickListener());
        return view;
    }


    private class OnGetRatingClickListener implements View.OnClickListener{
        @Override
        public void onClick(View view) {
            try {ConnectFragment.this.finalize();
            } catch (Throwable throwable) {
                throwable.printStackTrace();
            }
        }
    }
}