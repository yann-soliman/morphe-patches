package io.github.yannsoliman.naolib.microgmaps;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

/** Separate screen so the host never continues with partially initialized Maps. */
public final class MicrogRequiredActivity extends Activity {
    static final String DOWNLOAD = "https://morphe.software/microg";

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        new AlertDialog.Builder(this)
            .setTitle("microG requis pour la carte")
            .setMessage("Installez Morphe microG avec son moteur de carte, puis relancez Naolib. Gardez les services Google Play installés.\n\n" + DOWNLOAD)
            .setPositiveButton("Télécharger microG", (dialog, which) -> {
                try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(DOWNLOAD))); }
                catch (ActivityNotFoundException noBrowser) {
                    Toast.makeText(this, DOWNLOAD, Toast.LENGTH_LONG).show();
                }
                finish();
            })
            .setNegativeButton("Fermer", (dialog, which) -> finish())
            .setOnCancelListener(dialog -> finish())
            .show();
    }
}
