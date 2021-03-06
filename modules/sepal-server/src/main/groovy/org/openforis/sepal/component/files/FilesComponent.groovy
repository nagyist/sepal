package org.openforis.sepal.component.files

import groovymvc.Controller
import org.openforis.sepal.component.NonTransactionalComponent
import org.openforis.sepal.component.files.command.DeleteFile
import org.openforis.sepal.component.files.command.DeleteFileHandler
import org.openforis.sepal.component.files.endpoint.FilesEndpoint
import org.openforis.sepal.component.files.query.ListFiles
import org.openforis.sepal.component.files.query.ListFilesHandler
import org.openforis.sepal.component.files.query.ReadFile
import org.openforis.sepal.component.files.query.ReadFileHandler
import org.openforis.sepal.endpoint.EndpointRegistry

class FilesComponent extends NonTransactionalComponent implements EndpointRegistry {
    FilesComponent(File homeDir) {
        query(ListFiles, new ListFilesHandler(homeDir))
        query(ReadFile, new ReadFileHandler(homeDir))
        command(DeleteFile, new DeleteFileHandler(homeDir))
    }

    void registerEndpointsWith(Controller controller) {
        new FilesEndpoint(this).registerWith(controller)
    }
}
