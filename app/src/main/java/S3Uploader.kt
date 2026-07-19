package lk.kdu.ac.mc.newlifelog

import android.content.Context
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.regions.Region
import com.amazonaws.regions.Regions
import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.services.s3.model.PutObjectRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class S3Uploader(context: Context) {

    private val s3Client: AmazonS3Client

    init {
        // ආරක්ෂිතව යතුරු දෙක ලබා ගැනීම
        val credentials = BasicAWSCredentials(BuildConfig.AWS_ACCESS_KEY, BuildConfig.AWS_SECRET_KEY)
        s3Client = AmazonS3Client(credentials)

        // ඔයාගේ Bucket එක තියෙන Region එක
        s3Client.setRegion(Region.getRegion(Regions.AP_SOUTHEAST_1))
    }

    // ෆොටෝ සහ රෙකෝඩින් අප්ලෝඩ් කරන Function එක
    suspend fun uploadMedia(file: File, fileName: String): Boolean {
        return withContext(Dispatchers.IO) { // UI එක හිර වෙන්නේ නැති වෙන්න Background එකේ වැඩේ වෙනවා
            try {
                val bucketName = "venuri-lifelog-media"
                val putObjectRequest = PutObjectRequest(bucketName, fileName, file)

                s3Client.putObject(putObjectRequest)
                true // අප්ලෝඩ් එක සාර්ථකයි

            } catch (e: Exception) {
                e.printStackTrace()
                false // අප්ලෝඩ් එක අසාර්ථකයි
            }
        }
    }
}