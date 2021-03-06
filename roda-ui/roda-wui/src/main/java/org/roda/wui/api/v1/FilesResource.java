/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE file at the root of the source
 * tree and available online at
 *
 * https://github.com/keeps/roda
 */
package org.roda.wui.api.v1;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.xml.transform.TransformerException;

import org.glassfish.jersey.media.multipart.FormDataContentDisposition;
import org.glassfish.jersey.media.multipart.FormDataParam;
import org.roda.core.common.EntityResponse;
import org.roda.core.common.StreamResponse;
import org.roda.core.common.UserUtility;
import org.roda.core.data.common.RodaConstants;
import org.roda.core.data.exceptions.GenericException;
import org.roda.core.data.exceptions.RODAException;
import org.roda.core.data.v2.common.Pair;
import org.roda.core.data.v2.index.IndexResult;
import org.roda.core.data.v2.index.filter.Filter;
import org.roda.core.data.v2.index.sort.Sorter;
import org.roda.core.data.v2.index.sublist.Sublist;
import org.roda.core.data.v2.ip.Files;
import org.roda.core.data.v2.ip.IndexedFile;
import org.roda.core.data.v2.ip.metadata.OtherMetadata;
import org.roda.core.data.v2.ip.metadata.OtherMetadataList;
import org.roda.core.data.v2.ip.metadata.PreservationMetadata;
import org.roda.core.data.v2.ip.metadata.PreservationMetadataList;
import org.roda.core.data.v2.user.User;
import org.roda.wui.api.controllers.Browser;
import org.roda.wui.api.v1.utils.ApiResponseMessage;
import org.roda.wui.api.v1.utils.ApiUtils;
import org.roda.wui.api.v1.utils.ExtraMediaType;
import org.roda.wui.api.v1.utils.ObjectResponse;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;

@Path(FilesResource.ENDPOINT)
@Api(value = FilesResource.SWAGGER_ENDPOINT)
public class FilesResource {
  public static final String ENDPOINT = "/v1/files";
  public static final String SWAGGER_ENDPOINT = "v1 files";

  @Context
  private HttpServletRequest request;

  @GET
  @Consumes({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
  @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
  @ApiOperation(value = "List Files", notes = "Gets a list of files.", response = Files.class, responseContainer = "List")
  @ApiResponses(value = {
    @ApiResponse(code = 200, message = "Successful response", response = Files.class, responseContainer = "List"),
    @ApiResponse(code = 404, message = "Not found", response = ApiResponseMessage.class)})

  public Response listFiles(
    @ApiParam(value = "Index of the first element to return", defaultValue = "0") @QueryParam(RodaConstants.API_QUERY_KEY_START) String start,
    @ApiParam(value = "Maximum number of elements to return", defaultValue = RodaConstants.DEFAULT_PAGINATION_STRING_VALUE) @QueryParam(RodaConstants.API_QUERY_KEY_LIMIT) String limit,
    @ApiParam(value = "Choose format in which to get the file", allowableValues = RodaConstants.API_LIST_MEDIA_TYPES, defaultValue = RodaConstants.API_QUERY_VALUE_ACCEPT_FORMAT_JSON) @QueryParam(RodaConstants.API_QUERY_KEY_ACCEPT_FORMAT) String acceptFormat)
    throws RODAException {
    String mediaType = ApiUtils.getMediaType(acceptFormat, request);

    // get user
    User user = UserUtility.getApiUser(request);

    // delegate action to controller
    boolean justActive = false;
    Pair<Integer, Integer> pagingParams = ApiUtils.processPagingParams(start, limit);
    IndexResult<IndexedFile> result = Browser.find(IndexedFile.class, Filter.NULL, Sorter.NONE,
      new Sublist(pagingParams.getFirst(), pagingParams.getSecond()), null, user, justActive, new ArrayList<>());
    return Response.ok(ApiUtils.indexedResultToRODAObjectList(IndexedFile.class, result), mediaType).build();
  }

  @GET
  @Path("/{" + RodaConstants.API_PATH_PARAM_FILE_UUID + "}")
  @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, MediaType.APPLICATION_OCTET_STREAM})
  @ApiOperation(value = "Get file", notes = "Get file", response = org.roda.core.data.v2.ip.File.class)
  @ApiResponses(value = {@ApiResponse(code = 200, message = "OK", response = org.roda.core.data.v2.ip.File.class),
    @ApiResponse(code = 404, message = "Not found", response = ApiResponseMessage.class)})

  public Response retrieve(
    @ApiParam(value = "The UUID of the existing file", required = true) @PathParam(RodaConstants.API_PATH_PARAM_FILE_UUID) String fileUUID,
    @ApiParam(value = "Choose format in which to get the file", allowableValues = RodaConstants.API_GET_FILE_MEDIA_TYPES) @QueryParam(RodaConstants.API_QUERY_KEY_ACCEPT_FORMAT) String acceptFormat,
    @QueryParam(RodaConstants.API_QUERY_KEY_INLINE) boolean inline) throws RODAException {
    String mediaType = ApiUtils.getMediaType(acceptFormat, request);

    // get user
    User user = UserUtility.getApiUser(request);

    // delegate action to controller
    EntityResponse efile = Browser.retrieveAIPRepresentationFile(user, fileUUID, acceptFormat);

    if (efile instanceof ObjectResponse) {
      ObjectResponse<org.roda.core.data.v2.ip.File> file = (ObjectResponse<org.roda.core.data.v2.ip.File>) efile;
      return Response.ok(file.getObject(), mediaType).build();
    } else {
      return ApiUtils.okResponse((StreamResponse) efile, inline);
    }
  }

  @PUT
  @ApiOperation(value = "Update file", notes = "Update existing file", response = org.roda.core.data.v2.ip.File.class)
  @ApiResponses(value = {@ApiResponse(code = 200, message = "OK", response = org.roda.core.data.v2.ip.File.class),
    @ApiResponse(code = 404, message = "Not found", response = ApiResponseMessage.class)})

  public Response update(org.roda.core.data.v2.ip.File file,
    @FormDataParam(RodaConstants.API_PARAM_UPLOAD) InputStream inputStream,
    @FormDataParam(RodaConstants.API_PARAM_UPLOAD) FormDataContentDisposition fileDetail,
    @ApiParam(value = "Choose format in which to get the file", allowableValues = RodaConstants.API_POST_PUT_MEDIA_TYPES) @QueryParam(RodaConstants.API_QUERY_KEY_ACCEPT_FORMAT) String acceptFormat)
    throws RODAException {
    String mediaType = ApiUtils.getMediaType(acceptFormat, request);

    // get user
    User user = UserUtility.getApiUser(request);

    // delegate action to controller
    try {
      boolean createIfNotExists = true;
      boolean notify = true;
      org.roda.core.data.v2.ip.File updatedFile = Browser.updateFile(user, file, inputStream, createIfNotExists,
        notify);
      return Response.ok(updatedFile, mediaType).build();
    } catch (IOException e) {
      return ApiUtils.errorResponse(new TransformerException(e.getMessage()));
    }
  }

  @POST
  @ApiOperation(value = "Create file", notes = "Create a new representation file", response = org.roda.core.data.v2.ip.File.class)
  @ApiResponses(value = {@ApiResponse(code = 200, message = "OK", response = org.roda.core.data.v2.ip.File.class),
    @ApiResponse(code = 409, message = "Already exists", response = ApiResponseMessage.class)})

  public Response createRepresentationFile(
    @ApiParam(value = "The AIP ID of the new file") @QueryParam(RodaConstants.API_PATH_PARAM_AIP_ID) String aipId,
    @ApiParam(value = "The representation ID of the new file") @QueryParam(RodaConstants.API_PATH_PARAM_REPRESENTATION_ID) String representationId,
    @ApiParam(value = "The directory path of the parent folder") @QueryParam(RodaConstants.API_PATH_PARAM_FOLDER) List<String> folderPath,
    @FormDataParam(RodaConstants.API_PARAM_UPLOAD) InputStream inputStream,
    @FormDataParam(RodaConstants.API_PARAM_UPLOAD) FormDataContentDisposition fileDetail,
    @ApiParam(value = "A new filename to this file") @QueryParam(RodaConstants.API_QUERY_KEY_FILENAME) String filename,
    @ApiParam(value = "Reason to upload file") @QueryParam(RodaConstants.API_QUERY_PARAM_DETAILS) String details,
    @ApiParam(value = "Choose format in which to get the file", allowableValues = RodaConstants.API_POST_PUT_MEDIA_TYPES) @QueryParam(RodaConstants.API_QUERY_KEY_ACCEPT_FORMAT) String acceptFormat)
    throws RODAException {
    String mediaType = ApiUtils.getMediaType(acceptFormat, request);

    // get user
    User user = UserUtility.getApiUser(request);

    // delegate action to controller
    try {
      String name = filename == null ? fileDetail.getFileName() : filename;

      org.roda.core.data.v2.ip.File file = Browser.createFile(user, aipId, representationId, folderPath, name,
        inputStream, details != null ? details : "");

      return Response.ok(file, mediaType).build();
    } catch (IOException e) {
      throw new GenericException(e);
    }
  }

  @DELETE
  @Path("/{" + RodaConstants.API_PATH_PARAM_FILE_UUID + "}")
  @ApiOperation(value = "Delete file", notes = "Delete representation file", response = Void.class)
  @ApiResponses(value = {@ApiResponse(code = 204, message = "OK", response = Void.class),
    @ApiResponse(code = 404, message = "Not found", response = ApiResponseMessage.class)})

  public Response delete(
    @ApiParam(value = "The UUID of the existing file", required = true) @PathParam(RodaConstants.API_PATH_PARAM_FILE_UUID) String fileUUID,
    @ApiParam(value = "Reason to remove file") @QueryParam(RodaConstants.API_QUERY_PARAM_DETAILS) String details,
    @ApiParam(value = "Choose format in which to get the response", allowableValues = RodaConstants.API_DELETE_MEDIA_TYPES) @QueryParam(RodaConstants.API_QUERY_KEY_ACCEPT_FORMAT) String acceptFormat)
    throws RODAException {
    String mediaType = ApiUtils.getMediaType(acceptFormat, request);

    // get user
    User user = UserUtility.getApiUser(request);
    String eventDetails = details == null ? "" : details;

    // delegate action to controller
    Browser.deleteFile(user, fileUUID, eventDetails);
    return Response.ok(new ApiResponseMessage(ApiResponseMessage.OK, "File deleted"), mediaType).build();
  }

  @GET
  @Path("/{" + RodaConstants.API_PATH_PARAM_FILE_UUID + "}/" + RodaConstants.API_PRESERVATION_METADATA + "/")
  @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, ExtraMediaType.APPLICATION_ZIP})
  @ApiOperation(value = "Get preservation metadata", notes = "Get preservation metadata (JSON info, ZIP file or HTML conversion).\nOptional query params of **start** and **limit** defined the returned array.", response = PreservationMetadataList.class)
  @ApiResponses(value = {@ApiResponse(code = 200, message = "OK", response = PreservationMetadataList.class),
    @ApiResponse(code = 404, message = "Not found", response = ApiResponseMessage.class)})

  public Response retrievePreservationMetadataListFromAIP(
    @ApiParam(value = "The ID of the existing AIP", required = true) @PathParam(RodaConstants.API_PATH_PARAM_FILE_UUID) String fileId,
    @ApiParam(value = "Index of the first element to return", defaultValue = "0") @QueryParam(RodaConstants.API_QUERY_KEY_START) String start,
    @ApiParam(value = "Maximum number of elements to return", defaultValue = RodaConstants.DEFAULT_PAGINATION_STRING_VALUE) @QueryParam(RodaConstants.API_QUERY_KEY_LIMIT) String limit,
    @ApiParam(value = "Choose format in which to get the metadata", allowableValues = RodaConstants.API_GET_LIST_MEDIA_TYPES, defaultValue = RodaConstants.API_QUERY_VALUE_ACCEPT_FORMAT_JSON) @QueryParam(RodaConstants.API_QUERY_KEY_ACCEPT_FORMAT) String acceptFormat)
    throws RODAException {
    String mediaType = ApiUtils.getMediaType(acceptFormat, request);

    // get user
    User user = UserUtility.getApiUser(request);

    // delegate action to controller
    EntityResponse preservationMetadataList = Browser.retrieveAIPRepresentationPreservationMetadataFile(user, fileId,
      acceptFormat);

    if (preservationMetadataList instanceof ObjectResponse) {
      ObjectResponse<PreservationMetadataList> pmlist = (ObjectResponse<PreservationMetadataList>) preservationMetadataList;
      return Response.ok(pmlist.getObject(), mediaType).build();
    } else {
      return ApiUtils.okResponse((StreamResponse) preservationMetadataList);
    }
  }

  @POST
  @Path("/{" + RodaConstants.API_PATH_PARAM_FILE_UUID + "}/" + RodaConstants.API_PRESERVATION_METADATA + "/")
  @ApiOperation(value = "Create representation preservation file", notes = "Create a preservation file to a file", response = PreservationMetadata.class)
  @ApiResponses(value = {@ApiResponse(code = 200, message = "OK", response = PreservationMetadata.class),
    @ApiResponse(code = 404, message = "Not found", response = ApiResponseMessage.class)})

  public Response createPreservationMetadataOnFile(
    @ApiParam(value = "The ID of the existing file", required = true) @PathParam(RodaConstants.API_PATH_PARAM_FILE_UUID) String fileId,
    @FormDataParam(RodaConstants.API_PARAM_FILE) InputStream inputStream,
    @FormDataParam(RodaConstants.API_PARAM_FILE) FormDataContentDisposition fileDetail,
    @ApiParam(value = "Choose format in which to get the response", allowableValues = RodaConstants.API_POST_PUT_MEDIA_TYPES) @QueryParam(RodaConstants.API_QUERY_KEY_ACCEPT_FORMAT) String acceptFormat)
    throws RODAException {
    String mediaType = ApiUtils.getMediaType(acceptFormat, request);

    // get user
    User user = UserUtility.getApiUser(request);

    // delegate action to controller
    Browser.createOrUpdatePreservationMetadataWithFile(user, fileId, inputStream, true);
    return Response.ok(new ApiResponseMessage(ApiResponseMessage.OK, "Preservation file created"), mediaType).build();
  }

  @PUT
  @Path("/{" + RodaConstants.API_PATH_PARAM_FILE_UUID + "}/" + RodaConstants.API_PRESERVATION_METADATA + "/")
  @ApiOperation(value = "Update representation preservation file", notes = "Update a preservation file to a file", response = PreservationMetadata.class)
  @ApiResponses(value = {@ApiResponse(code = 200, message = "OK", response = PreservationMetadata.class),
    @ApiResponse(code = 404, message = "Not found", response = ApiResponseMessage.class)})

  public Response ypdatePreservationMetadataOnFile(
    @ApiParam(value = "The ID of the existing file", required = true) @PathParam(RodaConstants.API_PATH_PARAM_FILE_UUID) String fileId,
    @FormDataParam(RodaConstants.API_PARAM_FILE) InputStream inputStream,
    @FormDataParam(RodaConstants.API_PARAM_FILE) FormDataContentDisposition fileDetail,
    @ApiParam(value = "Choose format in which to get the response", allowableValues = RodaConstants.API_POST_PUT_MEDIA_TYPES) @QueryParam(RodaConstants.API_QUERY_KEY_ACCEPT_FORMAT) String acceptFormat)
    throws RODAException {
    String mediaType = ApiUtils.getMediaType(acceptFormat, request);

    // get user
    User user = UserUtility.getApiUser(request);

    // delegate action to controller
    Browser.createOrUpdatePreservationMetadataWithFile(user, fileId, inputStream, false);
    return Response.ok(new ApiResponseMessage(ApiResponseMessage.OK, "Preservation file updated"), mediaType).build();
  }

  /*** OTHER METADATA ****/

  @GET
  @Path("/{" + RodaConstants.API_PATH_PARAM_FILE_UUID + "}/" + RodaConstants.API_OTHER_METADATA + "/{"
    + RodaConstants.API_PATH_PARAM_OTHER_METADATA_TYPE + "}/{" + RodaConstants.API_PATH_PARAM_OTHER_METADATA_FILE_SUFFIX
    + "}")
  @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, ExtraMediaType.APPLICATION_ZIP})
  @ApiOperation(value = "Get other metadata", notes = "Get other metadata (JSON info or ZIP file).\nOptional query params of **start** and **limit** defined the returned array.", response = OtherMetadataList.class)
  @ApiResponses(value = {@ApiResponse(code = 200, message = "OK", response = OtherMetadataList.class),
    @ApiResponse(code = 404, message = "Not found", response = ApiResponseMessage.class)})

  public Response retrieveOtherMetadata(
    @ApiParam(value = "The UUID of the existing File", required = true) @PathParam(RodaConstants.API_PATH_PARAM_FILE_UUID) String fileUUID,
    @ApiParam(value = "The type of the other metadata", required = true) @PathParam(RodaConstants.API_PATH_PARAM_OTHER_METADATA_TYPE) String type,
    @ApiParam(value = "The file suffix of the other metadata", required = true) @PathParam(RodaConstants.API_PATH_PARAM_OTHER_METADATA_FILE_SUFFIX) String suffix,
    @ApiParam(value = "Choose format in which to get the metadata", allowableValues = RodaConstants.API_GET_FILE_MEDIA_TYPES, defaultValue = RodaConstants.API_QUERY_VALUE_ACCEPT_FORMAT_JSON) @QueryParam(RodaConstants.API_QUERY_KEY_ACCEPT_FORMAT) String acceptFormat)
    throws RODAException {
    String mediaType = ApiUtils.getMediaType(acceptFormat, request);

    // get user
    User user = UserUtility.getApiUser(request);
    String fileSuffix = suffix;

    if (!fileSuffix.startsWith(".")) {
      fileSuffix = '.' + fileSuffix;
    }

    // delegate action to controller
    EntityResponse otherMetadata = Browser.retrieveOtherMetadata(user, fileUUID, type, fileSuffix, acceptFormat);

    if (otherMetadata instanceof ObjectResponse) {
      ObjectResponse<OtherMetadata> om = (ObjectResponse<OtherMetadata>) otherMetadata;
      return Response.ok(om.getObject(), mediaType).build();
    } else {
      return ApiUtils.okResponse((StreamResponse) otherMetadata);
    }
  }

  @POST
  @Path("/{" + RodaConstants.API_PATH_PARAM_FILE_UUID + "}/" + RodaConstants.API_OTHER_METADATA + "/{"
    + RodaConstants.API_PATH_PARAM_OTHER_METADATA_TYPE + "}")
  @ApiOperation(value = "Create other metadata file", notes = "Create a other metadata file", response = OtherMetadata.class)
  @ApiResponses(value = {@ApiResponse(code = 200, message = "OK", response = OtherMetadata.class),
    @ApiResponse(code = 404, message = "Not found", response = ApiResponseMessage.class)})

  public Response createOtherMetadata(
    @ApiParam(value = "The UUID of the existing File", required = true) @PathParam(RodaConstants.API_PATH_PARAM_FILE_UUID) String fileUUID,
    @ApiParam(value = "The type of the other metadata", required = true) @PathParam(RodaConstants.API_PATH_PARAM_OTHER_METADATA_TYPE) String type,
    @FormDataParam(RodaConstants.API_PARAM_FILE) InputStream inputStream,
    @FormDataParam(RodaConstants.API_PARAM_FILE) FormDataContentDisposition fileDetail,
    @ApiParam(value = "Choose format in which to get the response", allowableValues = RodaConstants.API_POST_PUT_MEDIA_TYPES) @QueryParam(RodaConstants.API_QUERY_KEY_ACCEPT_FORMAT) String acceptFormat)
    throws RODAException {
    String mediaType = ApiUtils.getMediaType(acceptFormat, request);

    // get user
    User user = UserUtility.getApiUser(request);

    // delegate action to controller
    Browser.createOrUpdateOtherMetadata(user, fileUUID, type, inputStream, fileDetail.getFileName());
    return Response.ok(new ApiResponseMessage(ApiResponseMessage.OK, "Other metadata file created"), mediaType).build();
  }

  @PUT
  @Path("/{" + RodaConstants.API_PATH_PARAM_FILE_UUID + "}/" + RodaConstants.API_OTHER_METADATA + "/{"
    + RodaConstants.API_PATH_PARAM_OTHER_METADATA_TYPE + "}")
  @ApiOperation(value = "Update other metadata file", notes = "Update other metadata file", response = OtherMetadata.class)
  @ApiResponses(value = {@ApiResponse(code = 200, message = "OK", response = OtherMetadata.class),
    @ApiResponse(code = 404, message = "Not found", response = ApiResponseMessage.class)})

  public Response updateOtherMetadata(
    @ApiParam(value = "The UUID of the existing File", required = true) @PathParam(RodaConstants.API_PATH_PARAM_FILE_UUID) String fileUUID,
    @ApiParam(value = "The type of the other metadata", required = true) @PathParam(RodaConstants.API_PATH_PARAM_OTHER_METADATA_TYPE) String type,
    @FormDataParam(RodaConstants.API_PARAM_FILE) InputStream inputStream,
    @FormDataParam(RodaConstants.API_PARAM_FILE) FormDataContentDisposition fileDetail,
    @ApiParam(value = "Choose format in which to get the response", allowableValues = RodaConstants.API_POST_PUT_MEDIA_TYPES) @QueryParam(RodaConstants.API_QUERY_KEY_ACCEPT_FORMAT) String acceptFormat)
    throws RODAException {
    String mediaType = ApiUtils.getMediaType(acceptFormat, request);

    // get user
    User user = UserUtility.getApiUser(request);

    // delegate action to controller
    Browser.createOrUpdateOtherMetadata(user, fileUUID, type, inputStream, fileDetail.getFileName());
    return Response.ok(new ApiResponseMessage(ApiResponseMessage.OK, "Other metadata file updated"), mediaType).build();
  }

  @DELETE
  @Path("/{" + RodaConstants.API_PATH_PARAM_FILE_UUID + "}/" + RodaConstants.API_OTHER_METADATA + "/{"
    + RodaConstants.API_PATH_PARAM_OTHER_METADATA_TYPE + "}/{" + RodaConstants.API_PATH_PARAM_OTHER_METADATA_FILE_SUFFIX
    + "}")
  @ApiOperation(value = "Delete other metadata file", notes = "Delete other metadata file.", response = OtherMetadata.class)
  @ApiResponses(value = {@ApiResponse(code = 200, message = "OK", response = OtherMetadata.class),
    @ApiResponse(code = 404, message = "Not found", response = ApiResponseMessage.class)})

  public Response deleteOtherMetadata(
    @ApiParam(value = "The UUID of the existing File", required = true) @PathParam(RodaConstants.API_PATH_PARAM_FILE_UUID) String fileUUID,
    @ApiParam(value = "The type of the other metadata", required = true) @PathParam(RodaConstants.API_PATH_PARAM_OTHER_METADATA_TYPE) String type,
    @ApiParam(value = "The file suffix of the other metadata", required = true) @PathParam(RodaConstants.API_PATH_PARAM_OTHER_METADATA_FILE_SUFFIX) String suffix,
    @ApiParam(value = "Choose format in which to get the response", allowableValues = RodaConstants.API_DELETE_MEDIA_TYPES) @QueryParam(RodaConstants.API_QUERY_KEY_ACCEPT_FORMAT) String acceptFormat)
    throws RODAException {
    String mediaType = ApiUtils.getMediaType(acceptFormat, request);

    // get user
    User user = UserUtility.getApiUser(request);
    String fileSuffix = suffix;

    if (!fileSuffix.startsWith(".")) {
      fileSuffix = '.' + fileSuffix;
    }

    Browser.deleteOtherMetadata(user, fileUUID, fileSuffix, type);
    return Response.ok(new ApiResponseMessage(ApiResponseMessage.OK, "Other metadata file deleted"), mediaType).build();
  }

}
