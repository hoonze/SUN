package com.sun.tingle.file.service;

import ch.qos.logback.core.net.SyslogOutputStream;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.*;
import com.sun.tingle.calendar.service.CalendarService;
import com.sun.tingle.file.db.entity.MissionFileEntity;
import com.sun.tingle.file.db.entity.TeacherFileEntity;
import com.sun.tingle.file.db.repo.MissionFileRepository;
import com.sun.tingle.file.db.repo.TeacherFileRepository;
import com.sun.tingle.file.responsedto.MissionFileRpDto;
import com.sun.tingle.file.responsedto.TeacherFileRpDto;
import com.sun.tingle.mission.db.repo.MissionRepository;
import lombok.NoArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.PostConstruct;
import javax.naming.event.ObjectChangeListener;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

@Service
@NoArgsConstructor
public class S3service {

    @Autowired
    MissionFileRepository missionFileRepository;

    @Autowired
    TeacherFileRepository teacherFileRepository;

    @Autowired
    MissionRepository missionRepository;

    private AmazonS3 s3Client;

    @Value("${cloud.aws.credentials.accessKey}")
    private String accessKey;

    @Value("${cloud.aws.credentials.secretKey}")
    private String secretKey;

    @Value("${cloud.aws.s3.bucket}")
    private String bucket;

    @Value("${cloud.aws.region.static}")
    private String region;

    @ Value("${cloud.aws.credentials.profile-path}")
    private String bucketUrl;

    @PostConstruct
    public void setS3Client() {
        AWSCredentials credentials = new BasicAWSCredentials(this.accessKey, this.secretKey);

        s3Client = AmazonS3ClientBuilder.standard()
                .withCredentials(new AWSStaticCredentialsProvider(credentials))
                .withRegion(this.region)
                .build();
    }

    public String s3upload(MultipartFile file) throws IOException { //????????? ?????? ?????? ???
        String fileName = file.getOriginalFilename();
        int len = fileName.lastIndexOf(".");
        String fileNameE = fileName.substring(len,fileName.length());
        String randomUuid= UUID.randomUUID().toString().replaceAll("-","");
        randomUuid += fileNameE;
        s3Client.putObject(new PutObjectRequest(bucket, randomUuid, file.getInputStream(), null)
                .withCannedAcl(CannedAccessControlList.PublicRead));

        return s3Client.getUrl(bucket,randomUuid).toString().replace(bucketUrl,"");
    }


    public String s3folderIncludingUpload(MultipartFile file,String calendarCode,Long missionId) throws IOException { //s3??? ??????(????????? ?????? ??????)
        String fileName = file.getOriginalFilename();
        int len = fileName.lastIndexOf(".");
        String fileNameE = fileName.substring(len,fileName.length());
        String randomUuid= UUID.randomUUID().toString().replaceAll("-","");
        randomUuid += fileNameE;
        s3Client.putObject(new PutObjectRequest(bucket, calendarCode+"/"+missionId+"/"+randomUuid, file.getInputStream(), null)
                .withCannedAcl(CannedAccessControlList.PublicRead));

        return s3Client.getUrl(bucket,randomUuid).toString().replace(bucketUrl,"");
    }


    //????????? ????????? ?????? ???????????? ???
    public MissionFileRpDto missionFileUpload(MultipartFile file,Long missionId, Long id) throws IOException {
        String fileName = file.getOriginalFilename();
        String type = file.getContentType();
        String calendarCode = missionRepository.findByMissionId(missionId).getCalendarCode();
        String uuid = s3folderIncludingUpload(file,calendarCode,missionId);
        MissionFileEntity mEntity = new MissionFileEntity();
            mEntity = mEntity.builder().fileUuid(uuid).fileName(fileName).missionId(missionId).id(id).type(type).
                            build();

            mEntity = missionFileRepository.save(mEntity);
            MissionFileRpDto missionFileRpDto = buildMissionFile(mEntity);
            return missionFileRpDto;
    }


    public TeacherFileRpDto teacherFileUpload(MultipartFile file, Long id) throws IOException{
        String fileName = file.getOriginalFilename();
        int len = fileName.lastIndexOf(".");
        String fileNameE = fileName.substring(len,fileName.length());
        String randomUuid= UUID.randomUUID().toString().replaceAll("-","");
        randomUuid += fileNameE;
        s3Client.putObject(new PutObjectRequest(bucket, randomUuid, file.getInputStream(), null)
                .withCannedAcl(CannedAccessControlList.PublicRead));

        String file_uuid = s3Client.getUrl(bucket,randomUuid).toString().replace(bucketUrl,"");

        TeacherFileEntity teacherFileEntity = null;
        teacherFileEntity = teacherFileEntity.builder().fileUuid(randomUuid).fileName(fileName).id(id).build();
        teacherFileRepository.save(teacherFileEntity);
        TeacherFileRpDto teacherFileRpDto = buildTeacherFile(teacherFileEntity);

        return teacherFileRpDto;





    }

                        //?????? ?????? ??? ???????????? ?????? ???????????? ???
    public void teacherFileUploads(MultipartFile[] file, Long missionId, Long id) throws IOException {
        for(int i=0; i<file.length; i++) {
            String fileName = file[i].getOriginalFilename();
            String calendarCode = missionRepository.findByMissionId(missionId).getCalendarCode();
            String uuid = s3folderIncludingUpload(file[i],calendarCode,missionId);

            TeacherFileEntity tEntity = new TeacherFileEntity();
            tEntity = tEntity.builder().fileUuid(uuid).fileName(fileName).
                    missionId(missionId).id(id).
                    build();

            teacherFileRepository.save(tEntity);
        }
    }

    public int deleteMissionFile(String uuid,Long id) { // ???????????? ????????? ?????? ????????? ????????? ???
        MissionFileEntity m = missionFileRepository.findByFileUuid(uuid);
        if(m == null) {
            return 0; // ????????? ????????? ??????.
        }
        else if(m.getId() != id) {
            return 1; //?????? ??????
        }
        else {
            missionFileRepository.delete(m);
            String calendarCode = missionRepository.findByMissionId(m.getMissionId()).getCalendarCode();
//            String s3Uuid = uuid.replace("https://d101.s3.ap-northeast-2.amazonaws.com/","");
            s3Client.deleteObject(new DeleteObjectRequest(bucket,calendarCode+"/"+m.getMissionId()+"/"+uuid));
            return 2; // ?????? ??????
        }
    }

    public int deleteTeacherFile(String uuid,Long id) { // ????????? ????????? ?????? ????????? ????????? ???
        TeacherFileEntity m = teacherFileRepository.findByFileUuid(uuid);
        if(m == null) {
            return 0; // ????????? ????????? ??????.
        }
        else if(m.getId() != id) {
            return 1; //?????? ??????
        }
        else {
            teacherFileRepository.delete(m);
//            String calendarCode = missionRepository.findByMissionId(m.getMissionId()).getCalendarCode();
            s3Client.deleteObject(new DeleteObjectRequest(bucket,uuid));
            return 2; // ?????? ??????
        }
    }

//    public int deleteTeacherFile(String uuid,Long id) { // ????????? ????????? ?????? ????????? ????????? ???
//        TeacherFileEntity m = teacherFileRepository.findByFileUuid(uuid);
//        if(m == null) {
//            return 0; // ????????? ????????? ??????.
//        }
//        else if(m.getId() != id) {
//            return 1; //?????? ??????
//        }
//        else {
//            teacherFileRepository.delete(m);
//            String calendarCode = missionRepository.findByMissionId(m.getMissionId()).getCalendarCode();
//            s3Client.deleteObject(new DeleteObjectRequest(bucket,calendarCode+"/"+ m.getMissionId()+"/"+uuid));
//            return 2; // ?????? ??????
//        }
//    }


    public void deleteProfileFile(String uuid) { // S3??? ?????? ????????? ?????? ??????
//        String s3Uuid = uuid.replace("https://d101.s3.ap-northeast-2.amazonaws.com/","");
        s3Client.deleteObject(new DeleteObjectRequest(bucket,uuid));
    }




    public TeacherFileRpDto buildTeacherFile(TeacherFileEntity tEntity) {
        TeacherFileRpDto tDto = new TeacherFileRpDto();

        tDto = tDto.builder().fileName(tEntity.getFileName()).fileUuid(tEntity.getFileUuid()).
                fileId(tEntity.getFileId()).build();

        return tDto;
    }

    public  MissionFileRpDto buildMissionFile(MissionFileEntity mEntity) {
        MissionFileRpDto mDto = new MissionFileRpDto();

        mDto = mDto.builder().fileName(mEntity.getFileName())
                        .fileUuid(mEntity.getFileUuid()).type(mEntity.getType()).fileId(mEntity.getFileId()).
                build();

        return mDto;
    }

    public List<MissionFileRpDto> selectFileList(Long missionId) {
        List<MissionFileEntity> list = new ArrayList<>();
        List<MissionFileRpDto> list2 = new ArrayList<>();
        list = missionFileRepository.findByMissionId(missionId);

        for(int i=0; i<list.size(); i++) {
            MissionFileRpDto mDto = buildMissionFile(list.get(i));
            list2.add(mDto);
        }

        return list2;

    }

    public void s3CalendarDelete(String calendarCode) {
        ObjectListing objectListing = s3Client.listObjects(bucket,calendarCode);

        for(S3ObjectSummary s : objectListing.getObjectSummaries()) {
            s3Client.deleteObject(new DeleteObjectRequest(bucket,s.getKey()));
        }
    }


    public void s3MissionDelete(Long missionId) {
        String calendarCode = missionRepository.findByMissionId(missionId).getCalendarCode();

        ObjectListing objectListing = s3Client.listObjects(bucket,calendarCode+"/"+missionId);

        for(S3ObjectSummary s : objectListing.getObjectSummaries()) {
            s3Client.deleteObject(new DeleteObjectRequest(bucket,s.getKey()));
        }


    }




//    public void updateDeleteTeacherFile(String uuid) { // ???????????? ????????? ?????? ????????? ???
//        String s3Uuid = uuid.replace("https://d101.s3.ap-northeast-2.amazonaws.com/","");
//        s3Client.deleteObject(new DeleteObjectRequest(bucket,s3Uuid));
//    }
}
