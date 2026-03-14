package juloo.keyboard2;

import android.app.Activity;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.method.ScrollingMovementMethod;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import android.media.ToneGenerator;
import android.media.AudioManager;

public class TypingMasterActivity extends Activity {

    private TextView tvParagraph;
    private EditText etTypingArea;
    private Button btnNext;
    private ToneGenerator toneGen;

    private String[] paragraphs = {
        "The quick brown fox jumps over the lazy dog. This classic sentence contains every letter of the alphabet and is often used for typing practice. Speed and accuracy are both important when you are learning to type professionally. Practice regularly to improve your muscle memory and cognitive motor skills effectively.",
        "Technology has revolutionized the way we live and work in the modern era. From smartphones to artificial intelligence, innovation continues to reshape our daily interactions. Understanding these digital tools is essential for staying competitive in the rapidly evolving global landscape of the twenty-first century.",
        "Learning to code opens up a world of possibilities for creative expression and problem solving. It allows you to build applications that can reach millions of users worldwide. Programming logic is a valuable skill that transcends industries, helping you think more clearly and structured about complex systems.",
        "Nature offers a profound sense of tranquility and beauty to those who take the time to observe it. Forests, mountains, and oceans provide essential ecosystems that support life on Earth. Protecting our environment is a critical responsibility for current and future generations to ensure a sustainable planet.",
        "Reading books is a wonderful way to expand your knowledge and explore different perspectives. Whether it is fiction or non-fiction, literature invites us into new worlds and ideas. Cultivating a habit of reading can significantly enhance your vocabulary and critical thinking abilities over a long lifetime.",
        "Effective communication is key to building strong relationships both personally and professionally. Listening actively and expressing ideas clearly are fundamental components of successful interaction. Mastering these soft skills can lead to better collaboration and understanding in any group setting or organization.",
        "Exercise is vital for maintaining a healthy lifestyle and improving your overall well-being. Regular physical activity strengthens the heart, builds muscles, and boosts your mood through the release of endorphins. Finding an activity you enjoy makes it easier to stay consistent with your fitness goals.",
        "Traveling allows us to experience new cultures and broaden our horizons in ways books cannot. Seeing the world firsthand fosters empathy and appreciation for the diversity of human life. Every journey provides unique stories and lessons that stay with us long after we return home to our daily lives.",
        "Music has a universal language that transcends boundaries and connects people across the globe. From classical symphonies to modern pop, rhythm and melody have the power to evoke deep emotions. Learning to play an instrument or simply appreciating music can be a source of great joy and inspiration.",
        "A positive mindset can transform your approach to challenges and help you overcome obstacles with resilience. Focusing on solutions rather than problems allows for more creative and effective decision-making. Developing a growth mindset encourages continuous learning and personal development throughout your entire career path."
    };

    private int currentParagraphIndex = 0;
    private long startTime;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_typing_master);

        tvParagraph = findViewById(R.id.tv_paragraph);
        etTypingArea = findViewById(R.id.et_typing_area);
        btnNext = findViewById(R.id.btn_next);
        
        tvParagraph.setMovementMethod(new ScrollingMovementMethod());
        toneGen = new ToneGenerator(AudioManager.STREAM_MUSIC, 100);

        loadParagraph();

        etTypingArea.addTextChangedListener(new TextWatcher() {
            private String lastValidText = "";

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                lastValidText = s.toString();
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (s.length() == 1 && before == 0) {
                    startTime = System.currentTimeMillis();
                }
            }

            @Override
            public void afterTextChanged(Editable s) {
                String currentText = s.toString();
                String targetText = paragraphs[currentParagraphIndex];

                if (currentText.isEmpty()) {
                    loadParagraphWithSelection(0);
                    return;
                }

                if (targetText.startsWith(currentText)) {
                    loadParagraphWithSelection(currentText.length());
                    if (currentText.equals(targetText)) {
                        long endTime = System.currentTimeMillis();
                        long timeTaken = endTime - startTime;
                        int words = targetText.split("\\s+").length;
                        double wpm = (words / (timeTaken / 60000.0));
                        Toast.makeText(TypingMasterActivity.this, "Perfect! Speed: " + Math.round(wpm) + " WPM", Toast.LENGTH_LONG).show();
                        btnNext.setEnabled(true);
                    }
                } else {
                    toneGen.startTone(ToneGenerator.TONE_PROP_BEEP, 150);
                    etTypingArea.removeTextChangedListener(this);
                    s.replace(0, s.length(), lastValidText);
                    etTypingArea.addTextChangedListener(this);
                }
            }
        });

        btnNext.setOnClickListener(v -> {
            currentParagraphIndex = (currentParagraphIndex + 1) % paragraphs.length;
            loadParagraph();
            etTypingArea.setText("");
            btnNext.setEnabled(false);
        });
    }

    private void loadParagraph() {
        loadParagraphWithSelection(0);
    }

    private void loadParagraphWithSelection(int index) {
        String text = paragraphs[currentParagraphIndex];
        android.text.SpannableString spannable = new android.text.SpannableString(text);
        
        // Highlight completed text
        if (index > 0) {
            spannable.setSpan(new android.text.style.ForegroundColorSpan(0xFF4CAF50), 0, index, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        
        // Indication where the user is
        if (index < text.length()) {
            spannable.setSpan(new android.text.style.BackgroundColorSpan(0xFFBBDEFB), index, index + 1, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            spannable.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.BOLD), index, index + 1, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        
        tvParagraph.setText(spannable);
        if (index == 0) tvParagraph.scrollTo(0, 0);
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (toneGen != null) {
            toneGen.release();
        }
    }
}
