package org.sunsetware.phocid.ui.views

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.constraintlayout.compose.Dimension
import com.ibm.icu.number.LocalizedNumberFormatter
import com.ibm.icu.number.Notation
import com.ibm.icu.number.NumberFormatter
import com.ibm.icu.number.Precision
import com.ibm.icu.text.DateFormat
import com.ibm.icu.util.MeasureUnit
import java.util.Date
import java.util.Locale
import org.sunsetware.phocid.Dialog
import org.sunsetware.phocid.MainViewModel
import org.sunsetware.phocid.R
import org.sunsetware.phocid.Strings
import org.sunsetware.phocid.data.Track
import org.sunsetware.phocid.ui.components.DialogBase
import org.sunsetware.phocid.ui.components.SingleLineText

@Stable
class TrackDetailsDialog(private val track: Track) : Dialog() {
    @Composable
    override fun Compose(viewModel: MainViewModel) {
        val scrollState = rememberScrollState()
        val content =
            remember(track) {
                fun formatterBase(): LocalizedNumberFormatter {
                    return NumberFormatter.withLocale(Locale.getDefault())
                        .notation(Notation.simple())
                }

                listOf(
                    Strings[R.string.track_details_title] to track.displayTitle,
                    Strings[R.string.track_details_artist] to track.displayArtist,
                    Strings[R.string.track_details_album] to track.displayAlbum,
                    Strings[R.string.track_details_album_artist] to track.displayAlbumArtist,
                    Strings[R.string.track_details_genre] to track.displayGenre,
                    Strings[R.string.track_details_year] to track.displayYear,
                    Strings[R.string.track_details_track_number] to track.displayNumber,
                    Strings[R.string.track_details_path] to track.path,
                    Strings[R.string.track_details_date_added] to
                        DateFormat.getInstance().format(Date(track.dateAdded * 1000)),
                    Strings[R.string.track_details_date_modified] to
                        DateFormat.getInstance().format(Date(track.version * 1000)),
                    Strings[R.string.track_details_size] to
                        formatterBase()
                            .unit(MeasureUnit.MEGABYTE)
                            .precision(Precision.maxFraction(1))
                            .format(track.size / 1048576.0)
                            .toString(),
                    Strings[R.string.track_details_format] to track.format,
                    Strings[R.string.track_details_sample_rate] to
                        formatterBase().unit(MeasureUnit.HERTZ).format(track.sampleRate).toString(),
                    Strings[R.string.track_details_bit_rate] to
                        formatterBase()
                            .unit(MeasureUnit.KILOBIT)
                            .perUnit(MeasureUnit.SECOND)
                            .precision(Precision.integer())
                            .format(track.bitRate / 1024.0)
                            .toString(),
                    Strings[R.string.track_details_bit_depth] to track.bitDepth.toString(),
                )
            }
        DialogBase(
            title = Strings[R.string.track_details],
            onConfirmOrDismiss = { viewModel.uiManager.closeDialog() },
        ) {
            ConstraintLayout(
                modifier =
                    Modifier.fillMaxWidth().verticalScroll(scrollState).padding(horizontal = 24.dp)
            ) {
                val refs = content.map { createRef() to createRef() }
                val horizontalBarrier = createEndBarrier(*refs.map { it.first }.toTypedArray())
                val verticalBarriers = refs.map { createBottomBarrier(it.first, it.second) }

                content.forEachIndexed { index, (title, content) ->
                    val (titleRef, contentRef) = refs[index]
                    val verticalBarrier = if (index > 0) verticalBarriers[index - 1] else null
                    val verticalMargin = if (index > 0) 4.dp else 0.dp
                    SingleLineText(
                        title,
                        color = MaterialTheme.colorScheme.primary,
                        modifier =
                            Modifier.constrainAs(titleRef) {
                                top.linkTo(verticalBarrier ?: parent.top, verticalMargin)
                                start.linkTo(parent.start)
                            },
                    )
                    Text(
                        content,
                        modifier =
                            Modifier.constrainAs(contentRef) {
                                top.linkTo(verticalBarrier ?: parent.top, verticalMargin)
                                start.linkTo(horizontalBarrier, margin = 16.dp)
                                end.linkTo(parent.end)
                                width = Dimension.fillToConstraints
                            },
                    )
                }
            }
        }
    }
}
