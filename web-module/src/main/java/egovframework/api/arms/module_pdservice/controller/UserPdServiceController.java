/*
 * @author Dongmin.lee
 * @since 2022-06-17
 * @version 22.06.17
 * @see <pre>
 *  Copyright (C) 2007 by 313 DEV GRP, Inc - All Rights Reserved
 *  Unauthorized copying of this file, via any medium is strictly prohibited
 *  Proprietary and confidential
 *  Written by 313 developer group <313@313.co.kr>, December 2010
 * </pre>
 */
package egovframework.api.arms.module_pdservice.controller;

import egovframework.api.arms.module_filerepository.model.FileRepositoryDTO;
import egovframework.api.arms.module_filerepository.service.FileRepository;
import egovframework.api.arms.module_pdservice.model.PdServiceDTO;
import egovframework.api.arms.module_pdservice.service.PdService;
import egovframework.api.arms.util.PropertiesReader;
import egovframework.com.ext.jstree.springHibernate.core.controller.SHVAbstractController;
import egovframework.com.ext.jstree.springHibernate.core.vo.JsTreeHibernateSearchDTO;
import egovframework.com.utl.fcc.service.EgovFileUploadUtil;
import egovframework.com.utl.fcc.service.EgovFormBasedFileVo;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.criterion.Order;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.ui.ModelMap;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import org.springframework.web.servlet.ModelAndView;

import javax.annotation.PostConstruct;
import javax.servlet.http.HttpServletRequest;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.NoSuchElementException;

@Slf4j
@Controller
@CrossOrigin(origins = "http://localhost:9999")
@RequestMapping(value = {"/auth-user/api/arms/pdservice"})
public class UserPdServiceController extends SHVAbstractController<PdService, PdServiceDTO> {

    @Autowired
    @Qualifier("pdService")
    private PdService pdService;

    @Autowired
    @Qualifier("fileRepository")
    private FileRepository fileRepository;

    @PostConstruct
    public void initialize() {
        setJsTreeHibernateService(pdService);
    }

	private final Logger logger = LoggerFactory.getLogger(this.getClass());


    private static final Long ROOT_NODE_ID = new Long(2);
    private static final String NODE_TYPE = new String("default");

    @ResponseBody
    @RequestMapping(value = "/addEndNodeByRoot.do", method = RequestMethod.POST)
    public ModelAndView addEndNodeByRoot(PdServiceDTO pdServiceDTO,
                                         BindingResult bindingResult) throws Exception {
        if (bindingResult.hasErrors())
            throw new RuntimeException();

        //루트 노드를 기준으로 리스트를 검색
        PdServiceDTO paramPdServiceDTO = new PdServiceDTO();
        paramPdServiceDTO.setWhere("c_parentid", ROOT_NODE_ID);
        List<PdServiceDTO> list = pdService.getChildNode(paramPdServiceDTO);

        //검색된 노드중 maxPosition을 찾는다.
        PdServiceDTO maxPositionPdServiceDTO = list
                .stream()
                .max(Comparator.comparing(PdServiceDTO::getC_position))
                .orElseThrow(NoSuchElementException::new);

        logger.info("=======" + maxPositionPdServiceDTO);

        //노드 값 셋팅
        pdServiceDTO.setRef(ROOT_NODE_ID);
        pdServiceDTO.setC_position(maxPositionPdServiceDTO.getC_position() + 1);
        pdServiceDTO.setC_type(NODE_TYPE);

        ModelAndView modelAndView = new ModelAndView("jsonView");
        modelAndView.addObject("result", pdService.addNode(pdServiceDTO));

        return modelAndView;
    }

    /**
     * 이미지 Upload를 처리한다.
     *
     * @param multiRequest
     * @param model
     * @return
     * @throws Exception
     */
    @ResponseBody
    @RequestMapping(value="/uploadFileToNode.do")
    public ModelAndView uploadFileToNode(final MultipartHttpServletRequest multiRequest, @RequestParam("fileIdLink") Long fileIdLink, Model model) throws Exception {

        logger.info("fileIdLink -> " + fileIdLink);

        // Spring multipartResolver 미사용 시 (commons-fileupload 활용)
        //List<EgovFormBasedFileVo> list = EgovFormBasedFileUtil.uploadFiles(request, uploadDir, maxFileSize);

        // Spring multipartResolver 사용시
        PropertiesReader propertiesReader = new PropertiesReader("egovframework/egovProps/globals.properties");
        String uploadDir = propertiesReader.getProperty("Globals.fileStorePath");
        long maxFileSize = new Long(313);
        List<EgovFormBasedFileVo> list = EgovFileUploadUtil.uploadFiles(multiRequest, uploadDir, maxFileSize);

        for ( EgovFormBasedFileVo egovFormBasedFileVo : list) {

            FileRepositoryDTO fileRepositoryDTO = new FileRepositoryDTO();
            fileRepositoryDTO.setFileName(egovFormBasedFileVo.getFileName());
            fileRepositoryDTO.setContentType(egovFormBasedFileVo.getContentType());
            fileRepositoryDTO.setServerSubPath(egovFormBasedFileVo.getServerSubPath());
            fileRepositoryDTO.setPhysicalName(egovFormBasedFileVo.getPhysicalName());
            fileRepositoryDTO.setSize(egovFormBasedFileVo.getSize());
            fileRepositoryDTO.setName(egovFormBasedFileVo.getName());

            fileRepositoryDTO.setUrl(egovFormBasedFileVo.getUrl());
            //TODO: 썸네일 개발 필요
            fileRepositoryDTO.setThumbnailUrl(egovFormBasedFileVo.getThumbnailUrl());

            fileRepositoryDTO.setDelete_url(egovFormBasedFileVo.getDelete_url());
            fileRepositoryDTO.setDelete_type(egovFormBasedFileVo.getDelete_type());
            fileRepositoryDTO.setFileIdLink(fileIdLink);

            fileRepositoryDTO.setRef(new Long(1));
            fileRepositoryDTO.setC_title("pdService");
            fileRepositoryDTO.setC_type("default");

            FileRepositoryDTO returnFileRepositoryDTO = fileRepository.addNode(fileRepositoryDTO);
            //delete 파라미터인 id 값을 업데이트 치기 위해서.
            fileRepositoryDTO.setUrl("/auth-user/api/arms/fileRepository" + "/downloadFileByNode/" + returnFileRepositoryDTO.getId());
            fileRepositoryDTO.setThumbnailUrl("/auth-user/api/arms/fileRepository" + "/thumbnailUrlFileToNode/" + returnFileRepositoryDTO.getId());
            fileRepositoryDTO.setDelete_url("/auth-user/api/arms/fileRepository" + "/deleteFileByNode/" + returnFileRepositoryDTO.getId());

            fileRepository.updateNode(fileRepositoryDTO);

            egovFormBasedFileVo.setUrl("/auth-user/api/arms/fileRepository" + "/downloadFileByNode/" + returnFileRepositoryDTO.getId());
            egovFormBasedFileVo.setThumbnailUrl("/auth-user/api/arms/fileRepository" + "/thumbnailUrlFileToNode/" + returnFileRepositoryDTO.getId());
            egovFormBasedFileVo.setDelete_url("/auth-user/api/arms/fileRepository" + "/deleteFileByNode/" + returnFileRepositoryDTO.getId());

        }
        if (list.size() > 0) {
            EgovFormBasedFileVo vo = list.get(0);    // 첫번째 이미지

//            String url = multiRequest.getContextPath()
//                    + "/utl/web/imageSrc.do?"
//                    + "path=" + vo.getServerSubPath()
//                    + "&physical=" + vo.getPhysicalName()
//                    + "&contentType=" + vo.getContentType();

            //model.addAttribute("CKEditorFuncNum", request.getParameter("CKEditorFuncNum"));
            //model.addAttribute("url", url);
        }
        HashMap<String, List<EgovFormBasedFileVo>> map = new HashMap();
        map.put("files", list);
        ModelAndView modelAndView = new ModelAndView("jsonView");
        modelAndView.addObject("result", map);

        return modelAndView;
    }

    @RequestMapping(value="/updateContentsToNode.do", method=RequestMethod.POST)
    public ModelAndView updateContentsToNode(PdServiceDTO pdServiceDTO,
                                       BindingResult bindingResult) throws Exception {
        if (bindingResult.hasErrors())
            throw new RuntimeException();


        ModelAndView modelAndView = new ModelAndView("jsonView");
        modelAndView.addObject("result", pdService.updateContentsNode(pdServiceDTO));

        return modelAndView;
    }

    @RequestMapping(value="/updatePdServiceNode.do", method=RequestMethod.POST)
    public ModelAndView updatePdServiceNode(PdServiceDTO pdServiceDTO,
                                          BindingResult bindingResult) throws Exception {
        if (bindingResult.hasErrors())
            throw new RuntimeException();


        ModelAndView modelAndView = new ModelAndView("jsonView");
        modelAndView.addObject("result", pdService.updatePdServiceNode(pdServiceDTO));

        return modelAndView;
    }

    @ResponseBody
    @RequestMapping(
            value = {"/getPdServiceMonitor.do"},
            method = {RequestMethod.GET}
    )
    public ModelAndView getPdServiceMonitor(PdServiceDTO pdServiceDTO, ModelMap model, HttpServletRequest request) throws Exception {
        pdServiceDTO.setOrder(Order.asc("c_id"));
        List<PdServiceDTO> list = this.pdService.getChildNode(pdServiceDTO);
        for (PdServiceDTO dto: list) {
            dto.setC_contents("force empty");
        }
        ModelAndView modelAndView = new ModelAndView("jsonView");
        modelAndView.addObject("result", list);
        return modelAndView;
    }
}