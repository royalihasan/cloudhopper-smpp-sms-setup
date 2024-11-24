package org.alpha;


import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.alpha.entity.SmppServerEntity;

import java.util.List;

@Path("/smpp-servers")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class SmppServerResource {
    @Inject
    SmppServerService smppServerService;
    // Endpoint to insert test data
    @GET
    @Path("/insert")
    @Produces(MediaType.TEXT_PLAIN)
    public String insertTestServers() {
        smppServerService.insertTestServers();
        return "Test servers inserted!";
    }

    @GET
    public List<SmppServerEntity> getAll() {
        return SmppServerEntity.listAll();
    }

    @POST
    @Transactional
    public SmppServerEntity create(SmppServerEntity smppServer) {
        smppServer.persist();
        return smppServer;
    }

    @PUT
    @Path("/{id}")
    @Transactional
    public SmppServerEntity update(@PathParam("id") Long id, SmppServerEntity smppServer) {
        SmppServerEntity existing = SmppServerEntity.findById(id);
        if (existing == null) {
            throw new NotFoundException("SMPP server not found");
        }
        existing.host = smppServer.host;
        existing.port = smppServer.port;
        existing.systemId = smppServer.systemId;
        existing.password = smppServer.password;
        existing.priority = smppServer.priority;
        existing.status = smppServer.status;
        return existing;
    }

    @DELETE
    @Path("/{id}")
    @Transactional
    public void delete(@PathParam("id") Long id) {
        SmppServerEntity.deleteById(id);
    }
}
